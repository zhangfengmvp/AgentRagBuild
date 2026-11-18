package com.example.rag.rag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Rerank：对融合后的候选调用百炼 qwen3.7-text-rerank 做二次精排。
 *
 * 端点（百炼官方文档确认）：
 *   POST {workspace}.cn-beijing.maas.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank
 * 请求体：{ "model": "qwen3.7-text-rerank", "input": { "query": ..., "documents": [...] },
 *          "parameters": { "top_n": N, "instruct": "..." } }
 * 响应：{ "output": { "results": [ { "index": i, "relevance_score": 0.xx }, ... ] } }（按分数降序）
 *   index 对应输入 documents 数组下标，因此直接映射到候选 Hit。
 *
 * 失败（网络/鉴权/模型）时降级为「融合顺序」，保证链路始终可用。
 */
@Service
public class Reranker {

    private static final Logger log = LoggerFactory.getLogger(Reranker.class);

    private final RestClient rest = RestClient.create();

    @Value("${rag.rerank.url}")
    private String url;

    @Value("${rag.rerank.api-key}")
    private String apiKey;

    private static final String MODEL = "qwen3.7-text-rerank";
    private static final String INSTRUCT =
            "Given a web search query, retrieve relevant passages that answer the query.";

    public List<Hit> rerank(String query, List<Hit> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", MODEL,
                    "input", Map.of(
                            "query", query,
                            "documents", candidates.stream().map(Hit::text).toList()),
                    "parameters", Map.of(
                            "top_n", candidates.size(),
                            "instruct", INSTRUCT));

            RerankResponse resp = rest.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(RerankResponse.class);

            if (resp == null || resp.output() == null || resp.output().results() == null) {
                log.warn("Rerank 响应异常，降级为融合顺序");
                return candidates;
            }

            List<Hit> ranked = resp.output().results().stream()
                    .sorted((a, b) -> Double.compare(b.relevanceScore(), a.relevanceScore()))
                    .map(r -> {
                        int idx = Math.min(r.index(), candidates.size() - 1);
                        Hit h = candidates.get(idx);
                        return new Hit(h.docId(), h.source(), h.heading(), h.text(), r.relevanceScore(), "rerank");
                    }).toList();

            log.info("Rerank：候选 {} 条 → 重排 {} 条（top score={}）",
                    candidates.size(), ranked.size(), ranked.isEmpty() ? 0 : ranked.get(0).score());
            return ranked;
        } catch (Exception e) {
            log.warn("Rerank 调用失败，降级为融合顺序：{}", e.getMessage());
            return candidates;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RerankResponse(@JsonProperty("output") Output output) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Output(@JsonProperty("results") List<ResultItem> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ResultItem(@JsonProperty("index") int index,
                              @JsonProperty("relevance_score") double relevanceScore) {
    }
}
