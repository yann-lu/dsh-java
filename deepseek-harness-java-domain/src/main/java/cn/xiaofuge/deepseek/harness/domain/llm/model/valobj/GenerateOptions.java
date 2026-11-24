package cn.xiaofuge.deepseek.harness.domain.llm.model.valobj;

import java.util.List;
import java.util.Objects;

import cn.xiaofuge.deepseek.harness.domain.model.entity.Message;
import cn.xiaofuge.deepseek.harness.domain.model.valobj.ToolSchema;

/**
* GenerateOptions 表示GenerateOptions智能体选项。
*
* @param protocol 渠道协议编码（openai / anthropic / ollama）；空值按 openai 兼容协议处理
*/
public record GenerateOptions(
        String provider,
        String model,
        String reasoningEffort,
        List<Message> messages,
        String system,
        List<ToolSchema> tools,
        Double temperature,
        Integer maxTokens,
        List<String> stop,
        String sessionId,
        String purpose,
        String baseUrl,
        String apiKey,
        String protocol) {
        
        public GenerateOptions {
                Objects.requireNonNull(provider, "provider");
                Objects.requireNonNull(model, "model");
                messages = List.copyOf(messages);
                tools = tools == null ? null : List.copyOf(tools);
                stop = stop == null ? null : List.copyOf(stop);
        }
}
