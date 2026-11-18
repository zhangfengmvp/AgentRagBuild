package com.example.rag.agent;

import com.example.rag.rag.Citation;

import java.util.List;

/**
 * Agent 一次完整运行的产物：原始问题、逐步轨迹、最终答案、去重后的引用溯源。
 */
public record AgentResult(
        String question,
        List<AgentStep> steps,
        String finalAnswer,
        List<Citation> citations) {
}
