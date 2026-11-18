package com.example.rag.agent;

import com.example.rag.config.RagProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 语义缓存：对"问题 embedding 近邻命中"的历史回答做回放，省去重复检索与生成。
 * 仅缓存「含引用」的成功结果（避免缓存幻觉答案）。命中/未命中各记 Micrometer 计数。
 */
@Component
public class SemanticCache {

    private final EmbeddingModel embeddingModel;
    private final Cache<String, CachedItem> cache;
    private final double threshold;
    private final Counter hitCounter;
    private final Counter missCounter;

    public SemanticCache(EmbeddingModel embeddingModel, RagProperties props, MeterRegistry registry) {
        this.embeddingModel = embeddingModel;
        RagProperties.Cache cfg = props.cache();
        this.threshold = cfg.similarityThreshold();
        this.cache = Caffeine.newBuilder()
                .maximumSize(cfg.maxSize())
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .build();
        this.hitCounter = Counter.builder("rag.cache.hits").description("语义缓存命中次数").register(registry);
        this.missCounter = Counter.builder("rag.cache.misses").description("语义缓存未命中次数").register(registry);
    }

    public AgentResult get(String question) {
        if (question == null || question.isBlank()) {
            missCounter.increment();
            return null;
        }
        float[] q = embed(question);
        CachedItem best = null;
        double bestSim = -1;
        for (CachedItem item : cache.asMap().values()) {
            double sim = cosine(q, item.embedding());
            if (sim > bestSim) {
                bestSim = sim;
                best = item;
            }
        }
        if (best != null && bestSim >= threshold) {
            hitCounter.increment();
            return best.result();
        }
        missCounter.increment();
        return null;
    }

    public void put(String question, AgentResult result) {
        if (result == null || result.finalAnswer() == null || result.finalAnswer().isBlank()) {
            return;
        }
        if (result.citations() == null || result.citations().isEmpty()) {
            return; // 不缓存无引用的结果（可能是幻觉）
        }
        cache.put(question, new CachedItem(embed(question), result));
    }

    public long size() {
        return cache.estimatedSize();
    }

    private float[] embed(String text) {
        return embeddingModel.embed(text);
    }

    private record CachedItem(float[] embedding, AgentResult result) {
    }

    private static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return -1;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return -1;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
