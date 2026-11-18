package com.example.rag.rag;

import com.example.rag.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 检索：把问题向量化后在 Qdrant 中做相似度检索，返回 top-k 文档。
 * M3 将在此演进为混合检索 + Rerank + Query 改写。
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final VectorStore vectorStore;
    private final RagProperties props;

    public SearchService(VectorStore vectorStore, RagProperties props) {
        this.vectorStore = vectorStore;
        this.props = props;
    }

    public List<Document> search(String question) {
        try {
            List<Document> docs = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(question)
                    .topK(props.topK())
                    .similarityThreshold(props.similarityThreshold())
                    .build());
            logRetrieval(question, docs);
            return docs;
        } catch (RuntimeException e) {
            // collection 尚未创建（通常还没点过「入库语料」）→ 当作无结果，由上层提示先入库
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("NOT_FOUND") || msg.contains("doesn't exist")) {
                log.warn("Qdrant collection 不存在，可能尚未执行入库：{}", msg);
                return List.of();
            }
            throw e;
        }
    }

    /** 检索闭环日志：打印命中的片段来源/标题/相似度分数，用于确认“问题是否真的匹配到了正确切片”。 */
    private void logRetrieval(String question, List<Document> docs) {
        log.info("检索：问题='{}' → 命中 {} 个片段 (topK={}, threshold={})",
                question, docs.size(), props.topK(), props.similarityThreshold());
        for (int i = 0; i < docs.size(); i++) {
            Document d = docs.get(i);
            String source = String.valueOf(d.getMetadata().getOrDefault("source", "unknown"));
            String heading = String.valueOf(d.getMetadata().getOrDefault("heading", ""));
            log.info("  [#{}] score={}  source={}#{}", i + 1, d.getScore(), source, heading);
        }
    }
}
