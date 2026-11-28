package cn.xiaofuge.deepseek.harness.cases.agent;

import cn.xiaofuge.deepseek.harness.api.dto.AgentMessageRequestDTO;
import cn.xiaofuge.deepseek.harness.api.dto.AgentMessageResponseDTO;
import cn.xiaofuge.deepseek.harness.domain.llm.adapter.LlmAdapter;
import cn.xiaofuge.deepseek.harness.domain.llm.adapter.port.ILlmRuntimePort;
import cn.xiaofuge.deepseek.harness.domain.llm.model.valobj.FinishReason;
import cn.xiaofuge.deepseek.harness.domain.llm.model.valobj.GenerateOptions;
import cn.xiaofuge.deepseek.harness.domain.llm.model.valobj.LlmErrorCodes;
import cn.xiaofuge.deepseek.harness.domain.llm.model.valobj.LlmFailure;
import cn.xiaofuge.deepseek.harness.domain.llm.model.valobj.StreamChunk;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link AgentUseCase} 单元测试。
 * <p>
 * 覆盖收流模板对终态 Finish 的分流：Finish(Error) 不能被“空响应”分支吞掉，
 * 同时真正只有 [DONE] 的空流仍要给出“模型未返回任何内容”。
 */
class AgentUseCaseTest {

    private static final String ROUTING_FAILURE =
            "No adapter registered for provider 'not-exist'. Available providers: [deepseek]. "
                    + "Check providerCode in model settings.";

    /**
     * 用例 1：端口把路由失败编码成 Finish(Error) → 返回的 error 应是 LlmFailure 的人话，
     * 而不是误导性的“模型未返回任何内容”。
     */
    @Test
    void sendMessage_finishError_reportsLlmFailureInsteadOfEmptyContent() throws Exception {
        AgentUseCase useCase = useCaseWithPort(new FixedFinishPort(
                new FinishReason.Error(new LlmFailure(ROUTING_FAILURE, LlmErrorCodes.UNKNOWN))));

        AgentMessageResponseDTO response = useCase.sendMessage(request("not-exist"));

        assertEquals("模型调用失败：" + ROUTING_FAILURE, response.error());
        assertEquals(2, response.messages().size(), "用户消息和助手消息都应保留");
        assertEquals("", response.messages().get(1).get("content"), "路由失败时助手消息内容为空");
        System.out.println(response);
    }

    /**
     * 用例 2：正常结束但没有内容 → 仍然走空响应兜底，证明新增的 Finish(Error) 分支没有误伤 Stop。
     */
    @Test
    void sendMessage_stopWithoutContent_keepsEmptyResponseMessage() throws Exception {
        AgentUseCase useCase = useCaseWithPort(new FixedFinishPort(new FinishReason.Stop()));

        AgentMessageResponseDTO response = useCase.sendMessage(request("deepseek"));

        assertEquals("模型未返回任何内容", response.error());
        System.out.println(response);
    }

    /** 构造一个不依赖 Spring 容器的用例实例；defaultModel 平时由 @Value 注入。 */
    private static AgentUseCase useCaseWithPort(ILlmRuntimePort port) throws Exception {
        AgentUseCase useCase = new AgentUseCase();
        useCase.setBaseUrl("https://api.deepseek.com");
        useCase.setApiKey("test-api-key");
        useCase.setLlmRuntimePort(port);
        setField(useCase, "defaultModel", "deepseek-v4.1-flash");
        return useCase;
    }

    private static void setField(AgentUseCase useCase, String name, Object value) throws Exception {
        Field field = AgentUseCase.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(useCase, value);
    }

    private static AgentMessageRequestDTO request(String channelCode) {
        return new AgentMessageRequestDTO(
                "agent-1", channelCode, null, null, "你好", null, null, null, null);
    }

    /**
     * 只发布一个终态 Finish 的端口桩，模拟“路由失败 / 空流”两种收尾。
     */
    private static final class FixedFinishPort implements ILlmRuntimePort {

        private final FinishReason finishReason;

        FixedFinishPort(FinishReason finishReason) {
            this.finishReason = finishReason;
        }

        @Override
        public Flow.Publisher<StreamChunk> stream(GenerateOptions options) {
            StreamChunk.Finish finish = new StreamChunk.Finish(finishReason);
            return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {

                private final AtomicBoolean delivered = new AtomicBoolean();

                @Override
                public void request(long n) {
                    if (n <= 0 || !delivered.compareAndSet(false, true)) {
                        return;
                    }
                    subscriber.onNext(finish);
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                    delivered.set(true);
                }
            });
        }

        @Override
        public void registerAdapter(String provider, LlmAdapter adapter) {
        }

        @Override
        public void unregisterAdapter(String provider) {
        }

        @Override
        public List<LlmAdapter.LlmProviderInfo> listProviders() {
            return List.of();
        }

        @Override
        public List<LlmAdapter.LlmModelInfo> listModels(String provider) {
            return List.of();
        }

        @Override
        public LlmAdapter.LlmResolvedModelInfo resolveModel(String provider, String model) {
            throw new UnsupportedOperationException("not used in this test");
        }
    }
}
