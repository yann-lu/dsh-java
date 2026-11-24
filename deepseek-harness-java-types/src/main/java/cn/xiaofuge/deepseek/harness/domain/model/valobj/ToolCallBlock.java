package cn.xiaofuge.deepseek.harness.domain.model.valobj;

/**
 * 模型请求的一次工具调用
 */
public record ToolCallBlock(String text) implements ContentBlock{

}
