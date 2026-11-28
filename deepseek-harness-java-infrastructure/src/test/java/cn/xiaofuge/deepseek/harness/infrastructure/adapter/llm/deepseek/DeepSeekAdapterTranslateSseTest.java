package cn.xiaofuge.deepseek.harness.infrastructure.adapter.llm.deepseek;

import cn.xiaofuge.deepseek.harness.domain.llm.adapter.ResolvedRetryPolicy;
import cn.xiaofuge.deepseek.harness.domain.llm.model.valobj.FinishReason;
import cn.xiaofuge.deepseek.harness.domain.llm.model.valobj.StreamChunk;
import cn.xiaofuge.deepseek.harness.domain.model.valobj.TextBlock;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DeepSeekAdapter#translateSse} 单元测试。
 * <p>
 * 用本地假 SSE 文本替代真实 HTTP 流，验证分片到 {@link StreamChunk} 的翻译顺序：
 * 文本块开启 → 文本增量 → 块结束 → Finish。不发起任何网络请求。
 */
class DeepSeekAdapterTranslateSseTest {

    /**
     * 用例 1：5 行假 SSE（delta × 2 + [DONE]，中间夹 SSE 空行）→
     * 依次产出 BlockStart、两个 TextDelta、携带完整文本的 BlockEnd、Finish(Stop)。
     */
    @Test
    void translateSse_twoDeltasThenDone_emitsTextBlockThenFinish() throws Exception {
        // 5 行：第 1、3 行是 data delta，第 2、4 行是 SSE 空行，第 5 行是流结束标记
        List<String> sseLines = List.of(
                "data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}",
                "",
                "data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}",
                "",
                "data: [DONE]");
        String sse = String.join("\n", sseLines) + "\n";

        DeepSeekAdapter adapter = new DeepSeekAdapter(options(), new ObjectMapper());
        SubmissionPublisher<StreamChunk> publisher = new SubmissionPublisher<>();
        CollectingSubscriber subscriber = new CollectingSubscriber();
        publisher.subscribe(subscriber);

        boolean streamed;
        try (BufferedReader reader = new BufferedReader(new StringReader(sse))) {
            streamed = invokeTranslateSse(adapter, reader, publisher);
        } finally {
            publisher.close();
        }

        assertTrue(subscriber.awaitDone(5), "publisher.close() 后订阅者应收到 onComplete");
        assertNull(subscriber.error(), "流翻译不应向订阅者传递异常");
        assertTrue(streamed, "读到 [DONE] 时应返回 true");

        List<StreamChunk> chunks = subscriber.chunks();
        System.out.println(chunks);
        assertEquals(List.of(
                new StreamChunk.BlockStart(0, "text"),
                new StreamChunk.TextDelta(0, "你"),
                new StreamChunk.TextDelta(0, "好"),
                new StreamChunk.BlockEnd(0, new TextBlock("你好")),
                new StreamChunk.Finish(new FinishReason.Stop())
        ), chunks, "chunk 的顺序和内容应与 SSE 分片一一对应");
    }

    /**
     * 构造适配器所需配置；{@code translateSse} 只用到 ObjectMapper，其余字段仅为通过构造校验。
     */
    private static DeepSeekAdapterOptions options() {
        return new DeepSeekAdapterOptions(
                "https://api.deepseek.com",
                "test-api-key",
                DeepSeekAdapter.DEFAULT_MAX_TOKENS,
                DeepSeekAdapter.DEFAULT_CONTEXT_WINDOW,
                DeepSeekAdapter.DEFAULT_STREAM_IDLE_TIMEOUT_MS,
                List.of(),
                new ResolvedRetryPolicy.Normal(1000, 30_000, 0.2, 3, List.of("RATE_LIMIT")));
    }

    /**
     * translateSse 是适配器私有方法（与原工程保持一致的可见性），测试通过反射调用，
     * 避免为了可测性放宽生产代码的访问级别。
     */
    private static boolean invokeTranslateSse(DeepSeekAdapter adapter,
                                              BufferedReader reader,
                                              SubmissionPublisher<StreamChunk> publisher) throws Exception {
        Method method = DeepSeekAdapter.class.getDeclaredMethod(
                "translateSse", BufferedReader.class, SubmissionPublisher.class);
        method.setAccessible(true);
        try {
            return (boolean) method.invoke(adapter, reader, publisher);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }

    /**
     * 收集流式分片；onNext 与 onComplete 由 SubmissionPublisher 异步回调，用 latch 等待收尾。
     */
    private static final class CollectingSubscriber implements Flow.Subscriber<StreamChunk> {

        private final List<StreamChunk> chunks = new ArrayList<>();
        private final CountDownLatch done = new CountDownLatch(1);
        private final AtomicReference<Throwable> error = new AtomicReference<>();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(StreamChunk item) {
            chunks.add(item);
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

        boolean awaitDone(long seconds) throws InterruptedException {
            return done.await(seconds, TimeUnit.SECONDS);
        }

        List<StreamChunk> chunks() {
            return chunks;
        }

        Throwable error() {
            return error.get();
        }
    }
}
