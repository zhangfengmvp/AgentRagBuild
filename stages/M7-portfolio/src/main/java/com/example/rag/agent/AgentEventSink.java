package com.example.rag.agent;

/**
 * Agent 运行事件的推送抽象。实现方把事件编码为具体的传输帧（如 SSE）。
 * <ul>
 *   <li>{@link #emitStep(AgentStep)}：THOUGHT / ACTION / OBSERVATION / FINAL 步骤，供前端时间线可视化；</li>
 *   <li>{@link #emitToken(String)}：最终答案的流式 token（逐字推送）；</li>
 *   <li>{@link #complete()} / {@link #error(Throwable)}：正常结束或异常。</li>
 * </ul>
 */
public interface AgentEventSink {

    void emitStep(AgentStep step);

    void emitToken(String token);

    void complete();

    void error(Throwable t);
}
