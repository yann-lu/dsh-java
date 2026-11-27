package cn.xiaofuge.deepseek.harness.infrastructure.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import cn.xiaofuge.deepseek.harness.domain.llm.adapter.LlmAdapter;
import cn.xiaofuge.deepseek.harness.domain.llm.adapter.ResolvedRetryPolicy;
import cn.xiaofuge.deepseek.harness.domain.llm.adapter.port.ILlmRuntimePort;
import cn.xiaofuge.deepseek.harness.domain.llm.model.valobj.LlmErrorCodes;
import cn.xiaofuge.deepseek.harness.infrastructure.adapter.llm.InMemoryLlmRuntimePort;
import cn.xiaofuge.deepseek.harness.infrastructure.adapter.llm.deepseek.DeepSeekAdapter;
import cn.xiaofuge.deepseek.harness.infrastructure.adapter.llm.deepseek.DeepSeekAdapterOptions;
import cn.xiaofuge.deepseek.harness.infrastructure.adapter.llm.deepseek.DirectSeekCatalogModel;

/**
 * 基础设施装配配置。
 * <p>
 * 职责：把领域层端口与基础设施实现绑定，为触发层/用例层提供可注入的运行时依赖。
 * 当前阶段只装配「一次性对话」所需的大模型运行时：DeepSeek 适配器 + 内存路由表。
 *
 * @author luyan
 */
@Configuration
public class HarnessApplicationConfig {

    /**
     * 装配 DeepSeek 适配器。
     * <p>流程：读取 {@code harness.llm.deepseek.*} 配置 → 构造模型目录与重试策略 →
     * 组装 OpenAI 兼容协议的适配器实例。</p>
     */
    @Bean
    public DeepSeekAdapter deepSeekAdapter(
            ObjectMapper objectMapper,
            @Value("${harness.llm.deepseek.base-url}") String baseUrl,
            @Value("${harness.llm.deepseek.api-key}") String apiKey,
            @Value("${harness.llm.deepseek.max-tokens}") int maxTokens,
            @Value("${harness.llm.deepseek.context-window}") int contextWindow,
            @Value("${harness.llm.deepseek.default-model}") String defaultModel
    ) {
        List<DirectSeekCatalogModel> models = List.of(
                new DirectSeekCatalogModel(defaultModel, defaultModel, null, contextWindow, maxTokens)
        );
        ResolvedRetryPolicy retryPolicy = new ResolvedRetryPolicy.Normal(
                500, 10_000, 0.1, 2, LlmErrorCodes.DEFAULT_RETRYABLE_CODES
        );
        DeepSeekAdapterOptions options = new DeepSeekAdapterOptions(
                baseUrl,
                apiKey,
                maxTokens,
                contextWindow,
                DeepSeekAdapter.DEFAULT_STREAM_IDLE_TIMEOUT_MS,
                models,
                retryPolicy
        );
        return new DeepSeekAdapter(options, objectMapper);
    }

    /**
     * 装配大模型运行时端口。
     * <p>流程：把 Spring 容器中所有 {@link LlmAdapter} 实现按 {@code providerId}
     * 注册进内存路由表，后续 {@code stream} 调用即可按 provider 路由到对应适配器。</p>
     */
    @Bean
    public ILlmRuntimePort llmRuntimePort(List<LlmAdapter> adapters) {
        InMemoryLlmRuntimePort runtime = new InMemoryLlmRuntimePort();
        for (LlmAdapter adapter : adapters) {
            runtime.registerAdapter(adapter.providerId(), adapter);
        }
        return runtime;
    }

}
