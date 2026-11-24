package cn.xiaofuge.deepseek.harness.domain.llm.service;

import cn.xiaofuge.deepseek.harness.domain.llm.model.valobj.FinishReason;
import cn.xiaofuge.deepseek.harness.domain.llm.model.valobj.StreamChunk;
import cn.xiaofuge.deepseek.harness.domain.llm.model.valobj.TokenUsage;
import cn.xiaofuge.deepseek.harness.domain.model.valobj.ContentBlock;
import cn.xiaofuge.deepseek.harness.domain.model.valobj.TextBlock;
import cn.xiaofuge.deepseek.harness.domain.model.valobj.ToolCallBlock;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BlockAssembler 单元测试。
 * <p>
 * 验证流式分片到内容块的组装规则：正常文本流、ToolCall 分片、空流、乱序到达。
 */
class BlockAssemblerTest {

    /**
     * 用例 1：正常文本流（BlockStart → TextDelta × n → BlockEnd）→ 一个 TextBlock，内容完整。
     */
    @Test
    void push_textStream_assemblesSingleTextBlock() {
        BlockAssembler assembler = new BlockAssembler();

        assembler.push(new StreamChunk.BlockStart(0, "text"));
        assembler.push(new StreamChunk.TextDelta(0, "你"));
        assembler.push(new StreamChunk.TextDelta(0, "好，"));
        assembler.push(new StreamChunk.TextDelta(0, "世界"));
        assembler.push(new StreamChunk.BlockEnd(0, new TextBlock("你好，世界")));

        List<ContentBlock> blocks = assembler.blocks();
        assertEquals(1, blocks.size());
        assertEquals(new TextBlock("你好，世界"), blocks.get(0),
                "BlockEnd 携带的块应优先作为最终内容");
        System.out.println(blocks); // [TextBlock[text=你好，世界]]
    }

    /**
     * 用例 1b：没有 BlockEnd 兜底时，TextDelta 累积的内容也要能拼出来。
     */
    @Test
    void push_textStreamWithoutBlockEnd_assemblesFromAccumulatedDeltas() {
        BlockAssembler assembler = new BlockAssembler();

        assembler.push(new StreamChunk.BlockStart(0, "text"));
        assembler.push(new StreamChunk.TextDelta(0, "abc"));
        assembler.push(new StreamChunk.TextDelta(0, "def"));

        List<ContentBlock> blocks = assembler.blocks();
        assertEquals(1, blocks.size());
        assertInstanceOf(TextBlock.class, blocks.get(0));
        assertEquals("abcdef", ((TextBlock) blocks.get(0)).text());
        System.out.println(blocks); // [TextBlock[text=abcdef]]
    }

    /**
     * 用例 2：ToolCallDelta 分片（id/name 在第一片，arguments 分 3 片）→ ToolCallBlock 三字段拼对。
     */
    @Test
    void push_toolCallFragments_assembleAllThreeFields() {
        BlockAssembler assembler = new BlockAssembler();

        assembler.push(new StreamChunk.BlockStart(0, "tool-call"));
        assembler.push(new StreamChunk.ToolCallDelta(0, "call-001", "get_weather", "{\"city\""));
        assembler.push(new StreamChunk.ToolCallDelta(0, null, null, ": \"北京\""));
        assembler.push(new StreamChunk.ToolCallDelta(0, "", "", "}"));
        assembler.push(new StreamChunk.BlockEnd(0, null));

        List<ContentBlock> blocks = assembler.blocks();
        assertEquals(1, blocks.size());
        ToolCallBlock toolCall = assertInstanceOf(ToolCallBlock.class, blocks.get(0));
        assertEquals("call-001", toolCall.id());
        assertEquals("get_weather", toolCall.name());
        assertEquals("{\"city\": \"北京\"}", toolCall.arguments());
        System.out.println(blocks);
        System.out.println(toolCall);
    }

    /**
     * 用例 3：空流（只发 Usage + Finish）→ blocks() 为空，usage/finish 被记录，finish 默认 Stop。
     */
    @Test
    void push_usageAndFinishOnly_blocksEmptyButMetadataKept() {
        BlockAssembler assembler = new BlockAssembler();

        assembler.push(new StreamChunk.Usage(new TokenUsage(10, 5)));
        assembler.push(new StreamChunk.Finish(new FinishReason.ToolCalls()));

        assertTrue(assembler.blocks().isEmpty(), "无内容分片时不应产出任何块");
        assertEquals(new TokenUsage(10, 5), assembler.usage());
        assertEquals(new FinishReason.ToolCalls(), assembler.finish());
        System.out.println(assembler.blocks());
        System.out.println(assembler.usage());
        System.out.println(assembler.finish());
    }

    /**
     * 用例 3b：未收到 Finish 时 finish() 默认返回 Stop。
     */
    @Test
    void finish_withoutFinishChunk_defaultsToStop() {
        BlockAssembler assembler = new BlockAssembler();
        assertInstanceOf(FinishReason.Stop.class, assembler.finish());
    }

    /**
     * 用例 4：乱序（TextDelta 先于 BlockStart 到达）→ 不炸、内容不丢。
     */
    @Test
    void push_textDeltaBeforeBlockStart_noThrowNoLoss() {
        BlockAssembler assembler = new BlockAssembler();

        // TextDelta 先到（隐式创建 partial），BlockStart 后到（不应覆盖已有 partial）
        assembler.push(new StreamChunk.TextDelta(0, "先到的"));
        assembler.push(new StreamChunk.BlockStart(0, "text"));
        assembler.push(new StreamChunk.TextDelta(0, "内容"));

        List<ContentBlock> blocks = assembler.blocks();
        assertEquals(1, blocks.size(), "同一个 index 只应产出一个块");
        assertInstanceOf(TextBlock.class, blocks.get(0));
        assertEquals("先到的内容", ((TextBlock) blocks.get(0)).text(),
                "BlockStart 之前的 delta 不应丢失");
    }

    /**
     * 用例 4b：多个 index 乱序穿插时按首次出现顺序产出。
     */
    @Test
    void push_interleavedIndexes_blocksInFirstSeenOrder() {
        BlockAssembler assembler = new BlockAssembler();

        assembler.push(new StreamChunk.TextDelta(1, "B"));
        assembler.push(new StreamChunk.TextDelta(0, "A"));
        assembler.push(new StreamChunk.TextDelta(1, "b"));
        assembler.push(new StreamChunk.TextDelta(0, "a"));

        List<ContentBlock> blocks = assembler.blocks();
        // index 1 先首次出现，产出顺序应为 [1, 0]
        assertIterableEquals(List.of(new TextBlock("Bb"), new TextBlock("Aa")), blocks);
    }
}
