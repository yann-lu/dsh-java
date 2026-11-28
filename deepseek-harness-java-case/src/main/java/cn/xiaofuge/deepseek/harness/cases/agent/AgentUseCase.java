package cn.xiaofuge.deepseek.harness.cases.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import cn.xiaofuge.deepseek.harness.api.dto.AgentMessageRequestDTO;
import cn.xiaofuge.deepseek.harness.api.dto.AgentMessageResponseDTO;
import cn.xiaofuge.deepseek.harness.domain.llm.adapter.port.ILlmRuntimePort;
import cn.xiaofuge.deepseek.harness.domain.llm.model.valobj.FinishReason;
import cn.xiaofuge.deepseek.harness.domain.llm.model.valobj.GenerateOptions;
import cn.xiaofuge.deepseek.harness.domain.llm.model.valobj.StreamChunk;
import cn.xiaofuge.deepseek.harness.domain.llm.service.BlockAssembler;
import cn.xiaofuge.deepseek.harness.domain.model.entity.Message;
import cn.xiaofuge.deepseek.harness.domain.model.entity.MessageSource;
import cn.xiaofuge.deepseek.harness.domain.model.valobj.ContentBlock;
import cn.xiaofuge.deepseek.harness.domain.model.valobj.TextBlock;
import io.micrometer.common.util.StringUtils;

@Service
public class AgentUseCase implements IAgentUseCase {

    private static final Logger log = LoggerFactory.getLogger(AgentUseCase.class);

    // 类里加一个配置字段：
    @Value("${harness.llm.deepseek.default-provider:deepseek}")
    private String defaultProvider;
    @Value("${harness.llm.deepseek.base-url}")
    private String baseUrl;
    @Value("${harness.llm.deepseek.api-key}")
    private String apiKey;
    @Value("${harness.llm.deepseek.default-model:deepseek-v4.1-flash}")
    private String defaultModel;

    @Autowired
    private ILlmRuntimePort llmRuntimePort;

    @Override
    public AgentMessageResponseDTO sendMessage(AgentMessageRequestDTO request) {
        // 1. 校验agentId、message
        if (StringUtils.isBlank(request.agentId()) || StringUtils.isBlank(request.message())) {
            throw new IllegalArgumentException("agentId、message不能为空");
        }
        // 2. 组装用户消息
        Message userMessage = Message.createUser(List.of(new TextBlock(request.message())), new MessageSource.UserResources());
        // 3. 发起流式调用
        String sessionId = UUID.randomUUID().toString();
        GenerateOptions options = new GenerateOptions(StringUtils.isNotBlank(request.channelCode()) ? request.channelCode() : defaultProvider,
                                                      defaultModel,
                                                      "medium",
                                                      List.of(userMessage),
                                                      "user",
                                                      null,
                                                      1.0,
                                                      192,
                                                      null,
                                                      sessionId,
                                                      "",
                                                      baseUrl,
                                                      apiKey);
        var assembler = new BlockAssembler();
        var done = new CountDownLatch(1);
        var error = new AtomicReference<Throwable>();

        llmRuntimePort.stream(options).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamChunk item) {
                assembler.push(item);
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Agent={} 等待模型响应被中断", request.agentId(), e);
            error.compareAndSet(null, e);
        }

        // 4. 收流：流异常时仍保留已收到的部分内容
        Message assistantMessage = assembler.message(
                new MessageSource.ModelMessageSource(options.provider(), options.model()));
        List<Map<String, Object>> messages = List.of(toMessageMap(userMessage), toMessageMap(assistantMessage));

        String errorMessage = null;
        FinishReason finishReason = assembler.finish();
        if (error.get() != null) {
            Throwable cause = error.get();
            log.error("Agent={} 调用模型失败", request.agentId(), cause);
            errorMessage = "调用模型失败：" + (cause.getMessage() != null
                    ? cause.getMessage() : cause.getClass().getSimpleName());
        } else if (finishReason instanceof FinishReason.Error failed) {
            // 端口把路由失败等异常编码进 Finish(Error)，块内容会是空的，
            // 必须在这里把 LlmFailure 的人话还原给调用方，否则会被下面的空响应分支吞掉
            log.error("Agent={} 模型调用失败 code={} status={}：{}",
                    request.agentId(), failed.failure().code(), failed.failure().status(),
                    failed.failure().message());
            errorMessage = "模型调用失败：" + failed.failure().message();
        } else if (assistantMessage.content().isEmpty()) {
            // 模型可能只返回 [DONE]，空响应要给出明确错误
            errorMessage = "模型未返回任何内容";
        }

        // 5. 构建返回值
        return new AgentMessageResponseDTO(request.agentId(), sessionId, "idle", messages, null, errorMessage);
    }

    /** 把领域消息转换为前端协议所需的 Map（用户/助手消息只暴露 role + 纯文本）。 */
    private static Map<String, Object> toMessageMap(Message message) {
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : message.content()) {
            if (block instanceof TextBlock textBlock) {
                text.append(textBlock.text());
            }
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", message.role());
        map.put("content", text.toString());
        return map;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setLlmRuntimePort(ILlmRuntimePort llmRuntimePort) {
        this.llmRuntimePort = llmRuntimePort;
    }


}
