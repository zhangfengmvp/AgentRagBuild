package com.example.rag.rag;

import com.example.rag.config.RagProperties;
import com.example.rag.ingestion.Bm25Index;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 混合检索：稠密（Qdrant 向量）+ 稀疏（本地 BM25）并行召回，再用 RRF（Reciprocal Rank Fusion）融合。
 *
 * RRF 公式：每条候选的得分 = Σ 1 / (k + rank)，rank 为该候选在各自排序中的名次（从 1 开始）。
 * 不依赖两路分数的量纲，因此天然适配「余弦相似度」与「BM25 得分」这种量级不同的分数。
 */
@Service
public class HybridSearcher {

    private static final Logger log = LoggerFactory.getLogger(HybridSearcher.class);

    private final VectorStore vectorStore;
    private final Bm25Index bm25Index;
    private final RagProperties props;

    public HybridSearcher(VectorStore vectorStore, Bm25Index bm25Index, RagProperties props) {
        this.vectorStore = vectorStore;
        this.bm25Index = bm25Index;
        this.props = props;
    }

    public HybridResult search(String question) {
        int k = props.topK();

        // 1) 稠密检索（Qdrant 向量）
        List<Document> denseDocs = List.of();
        try {
            denseDocs = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(question)
                    .topK(k)
                    .similarityThreshold(props.similarityThreshold())
                    .build());
        } catch (RuntimeException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("NOT_FOUND") || msg.contains("doesn't exist")) {
                log.warn("Qdrant collection 不存在，可能尚未执行入库：{}", msg);
            } else {
                log.warn("稠密检索异常，降级为空（稀疏仍可兜底）：{}", msg);
            }
        }

        // 2) 稀疏检索（本地 BM25）
        List<Bm25Index.ScoredDoc> sparseDocs = props.sparseEnabled()
                ? bm25Index.search(question, k)
                : List.of();

        List<Hit> denseHits = denseDocs.stream().map(d -> new Hit(
                d.getId(),
                String.valueOf(d.getMetadata().getOrDefault("source", "unknown")),
                String.valueOf(d.getMetadata().getOrDefault("heading", "")),
                d.getText(),
                d.getScore() != null ? d.getScore() : 0.0,
                "dense")).toList();

        List<Hit> sparseHits = sparseDocs.stream().map(s -> new Hit(
                s.docId(), s.source(), s.heading(), s.text(), s.score(), "sparse")).toList();

        // 3) RRF 融合：按名次累加，docId 相同则合并（优先保留稠密命中，含向量分与正文）
        Map<String, Double> fusedScore = new LinkedHashMap<>();
        Map<String, Hit> fusedHit = new HashMap<>();
        int rank = 1;
        for (Hit h : denseHits) {
            fusedScore.merge(h.docId(), 1.0 / (props.rrfK() + rank), Double::sum);
            fusedHit.putIfAbsent(h.docId(), h);
            rank++;
        }
        rank = 1;
        for (Hit h : sparseHits) {
            fusedScore.merge(h.docId(), 1.0 / (props.rrfK() + rank), Double::sum);
            fusedHit.putIfAbsent(h.docId(), h);
            rank++;
        }
        List<Hit> fusedHits = new ArrayList<>();
        fusedScore.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(k)
                .forEach(e -> {
                    Hit base = fusedHit.get(e.getKey());
                    fusedHits.add(new Hit(base.docId(), base.source(), base.heading(),
                            base.text(), e.getValue(), "fused"));
                });

        log.info("混合检索：问题='{}' → 稠密 {} 条 / 稀疏 {} 条 → 融合 {} 条",
                question, denseHits.size(), sparseHits.size(), fusedHits.size());
        return new HybridResult(denseHits, sparseHits, fusedHits);
    }
}
