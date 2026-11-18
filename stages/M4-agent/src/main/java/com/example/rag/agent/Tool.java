package com.example.rag.agent;

import java.util.Map;

/**
 * Agent 工具抽象。与 Spring AI 的 {@code ToolCallback} 解耦：
 * 既能驱动本项目的手动 ReAct 循环，也方便日后替换为 MCP Tool。
 * 入参为扁平的 Map（对应 LLM 生成的 JSON 参数），返回给模型的文本。
 */
public interface Tool {

    /** 工具名（LLM 在 tool_calls 中引用的名字） */
    String name();

    /** 给 LLM 看的工具说明，决定何时调用 */
    String description();

    /** 入参的 JSON Schema 字符串，供 LLM 理解参数结构 */
    String inputSchema();

    /** 执行工具，返回给模型的观察文本 */
    String execute(Map<String, Object> args);
}
