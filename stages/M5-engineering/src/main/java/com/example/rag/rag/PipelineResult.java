package com.example.rag.rag;

import java.util.List;

/**
 * 增强问答页的一次完整响应：逐阶段可视化检索管道。
 * 原始问题 → (LLM 改写) → 稠密命中 / 稀疏命中 → RRF 融合 → Rerank → 最终答案 + 引用。
 */
public record PipelineResult(
        String originalQuery,
        String rewrittenQuery,
        List<Hit> denseHits,
        List<Hit> sparseHits,
        List<Hit> fusedHits,
        List<Hit> rerankedHits,
        String answer,
        List<Citation> citations) {
}
