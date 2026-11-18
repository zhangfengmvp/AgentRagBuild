package com.example.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 管道参数（对应 application.yml 的 rag.*）。
 */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(String corpusDir, int topK, Chunk chunk, double similarityThreshold) {

    public RagProperties(String corpusDir, int topK, Chunk chunk, double similarityThreshold) {
        this.corpusDir = corpusDir;
        this.topK = topK;
        this.chunk = chunk;
        // 相似度阈值缺省/非法时回退到 0.45：只保留"中等以上相关"的片段
        this.similarityThreshold = (similarityThreshold <= 0 || Double.isNaN(similarityThreshold)) ? 0.45 : similarityThreshold;
    }

    public record Chunk(int maxChars, int overlap) {
        public Chunk {
            if (maxChars <= 0) maxChars = 800;
            if (overlap < 0) overlap = 0;
            if (overlap >= maxChars) overlap = maxChars / 5;
        }
    }
}
