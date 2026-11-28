package cn.xiaofuge.deepseek.harness.infrastructure.adapter.llm;

import cn.xiaofuge.deepseek.harness.domain.llm.model.valobj.FinishReason;
import cn.xiaofuge.deepseek.harness.domain.llm.model.valobj.GenerateOptions;
import cn.xiaofuge.deepseek.harness.domain.llm.model.valobj.StreamChunk;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InMemoryLlmRuntimePort} 单元测试。
 * <p>
 * 覆盖 provider 路由失败时的流式终态：{@code stream()} 返回后调用方才订阅，
 * 也必须能收到携带路由失败原因的 Finish(Error)。
 */
class InMemoryLlmRuntimePortTest {

    /**
     * 用例 1：未注册 provider → 晚订阅也收到唯一一条 Finish(Error)，错误信息包含 provider 编码。
     */
    @Test
    void stream_unknownProvider_deliversErrorFinishToLateSubscriber() throws Exception {
        InMemoryLlmRuntimePort port = new InMemoryLlmRuntimePort();
        GenerateOptions options = new GenerateOptions(
                "not-exist",
                "any-model",
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "https://api.deepseek.com",
                "test-api-key");

        Flow.Publisher<StreamChunk> publisher = port.stream(options);

        CollectingSubscriber subscriber = new CollectingSubscriber();
        publisher.subscribe(subscriber);
        assertTrue(subscriber.awaitDone(5), "终态之后应收到 onComplete");
        assertNull(subscriber.error(), "路由失败应以 Finish(Error) 终态返回，而不是 onError");

        assertEquals(1, subscriber.chunks().size(), "应只收到一条终态 chunk");
        StreamChunk.Finish finish = assertInstanceOf(StreamChunk.Finish.class, subscriber.chunks().get(0));
        FinishReason.Error failure = assertInstanceOf(FinishReason.Error.class, finish.reason());
        assertTrue(failure.failure().message().contains("not-exist"),
                "错误信息应包含路由失败的 provider：" + failure.failure().message());
        System.out.println(subscriber.chunks());
    }

    /**
     * 收集流式分片；onNext 与 onComplete 可能异步回调，用 latch 等待收尾。
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
