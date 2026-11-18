package com.example.rag.rag;

/**
 * 一次检索命中：片段标识 + 来源 + 正文 + 分数 + 来源标记（dense/sparse/fused/rerank）。
 * 管道各阶段都复用它，便于前端逐步可视化。
 */
public record Hit(String docId, String source, String heading, String text, double score, String origin) {
}
