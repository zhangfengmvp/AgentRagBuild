package com.example.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 管道参数（对应 application.yml 的 rag.*）。
 * M3 在 M2 基础上扩展：rrfK / rerankTopN / queryRewriteEnabled / sparseEnabled。
 */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(String corpusDir,
                           int topK,
                           Chunk chunk,
                           double similarityThreshold,
                           int rrfK,
                           int rerankTopN,
                           boolean queryRewriteEnabled,
                           boolean sparseEnabled) {

    public RagProperties {
        if (similarityThreshold <= 0 || Double.isNaN(similarityThreshold)) similarityThreshold = 0.45;
        if (rrfK <= 0) rrfK = 60;
        if (rerankTopN <= 0) rerankTopN = 10;
    }

    public record Chunk(int maxChars, int overlap) {
        public Chunk {
            if (maxChars <= 0) maxChars = 800;
            if (overlap < 0) overlap = 0;
            if (overlap >= maxChars) overlap = maxChars / 5;
        }
    }
}
