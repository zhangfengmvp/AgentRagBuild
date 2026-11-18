package com.example.rag.agent;

/**
 * Agent 轨迹的单个步骤。phase 取值：THOUGHT（思考）/ ACTION（调用工具）/ OBSERVATION（工具返回）/ FINAL（最终答案）。
 * 用于前端时间线逐步可视化，把"思考→行动→观察"完整呈现。
 */
public record AgentStep(
        String phase,
        String toolName,
        String input,
        String output,
        long timestamp) {
}
