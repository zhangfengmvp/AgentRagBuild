package com.example.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 管道参数（对应 application.yml 的 rag.*）。
 * M3 在 M2 基础上扩展：rrfK / rerankTopN / queryRewriteEnabled / sparseEnabled。
 * M5 扩展：cache.*（语义缓存）、cost.*（成本单价，供成本看板使用）、wiki.*（LLM Wiki 知识库）。
 */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(String corpusDir,
                           int topK,
                           Chunk chunk,
                           double similarityThreshold,
                           int rrfK,
                           int rerankTopN,
                           boolean queryRewriteEnabled,
                           boolean sparseEnabled,
                           int agentMaxSteps,
                           boolean agentReactFallback,
                           Cache cache,
                           Cost cost,
                           Wiki wiki) {

    public RagProperties {
        if (similarityThreshold <= 0 || Double.isNaN(similarityThreshold)) similarityThreshold = 0.45;
        if (rrfK <= 0) rrfK = 60;
        if (rerankTopN <= 0) rerankTopN = 10;
        if (agentMaxSteps <= 0) agentMaxSteps = 6;
        if (cache == null) cache = new Cache(0.92, 200);
        if (cost == null) cost = new Cost(0.004, 0.012); // qwen 近似单价（元 / 1k tokens）
        if (wiki == null) wiki = new Wiki(true, true, "wiki/kg.json");
    }

    public record Chunk(int maxChars, int overlap) {
        public Chunk {
            if (maxChars <= 0) maxChars = 800;
            if (overlap < 0) overlap = 0;
            if (overlap >= maxChars) overlap = maxChars / 5;
        }
    }

    /** 语义缓存配置。 */
    public record Cache(double similarityThreshold, int maxSize) {
        public Cache {
            if (similarityThreshold <= 0 || Double.isNaN(similarityThreshold)) similarityThreshold = 0.92;
            if (maxSize <= 0) maxSize = 200;
        }
    }

    /** 成本单价（元 / 1k tokens），用于成本看板累计。 */
    public record Cost(double inputPricePer1k, double outputPricePer1k) {
        public Cost {
            if (inputPricePer1k < 0) inputPricePer1k = 0.004;
            if (outputPricePer1k < 0) outputPricePer1k = 0.012;
        }
    }

    /** LLM Wiki 配置：是否启用抽取、抽取粒度（按节）、图谱持久化路径。 */
    public record Wiki(boolean enabled, boolean extractPerSection, String storePath) {
        public Wiki {
            if (storePath == null || storePath.isBlank()) storePath = "wiki/kg.json";
        }
    }
}
