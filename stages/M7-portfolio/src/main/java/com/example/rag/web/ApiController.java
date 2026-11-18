package com.example.rag.web;

import com.example.rag.ingestion.IngestionService;
import com.example.rag.metrics.CostMeter;
import com.example.rag.rag.PipelineResult;
import com.example.rag.rag.PipelineService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API：/api/ask 问答（返回完整管道结果，含各阶段中间产物）；/api/ingest 触发语料入库（稠密+稀疏同步）。
 * /api/metrics/cost 返回成本看板快照（累计 token/成本 + 最近调用明细）。
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final PipelineService pipelineService;
    private final IngestionService ingestionService;
    private final CostMeter costMeter;

    public ApiController(PipelineService pipelineService, IngestionService ingestionService, CostMeter costMeter) {
        this.pipelineService = pipelineService;
        this.ingestionService = ingestionService;
        this.costMeter = costMeter;
    }

    public record AskRequest(String question) {
    }

    @PostMapping("/ask")
    public PipelineResult ask(@RequestBody AskRequest req) {
        if (req == null || req.question() == null || req.question().isBlank()) {
            throw new IllegalArgumentException("question 不能为空");
        }
        return pipelineService.run(req.question().trim());
    }

    @PostMapping("/ingest")
    public Map<String, Object> ingest() throws Exception {
        IngestionService.IngestResult r = ingestionService.ingest();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("files", r.files());
        m.put("chunks", r.chunks());
        m.put("message", "入库完成（解析 → 切片 → embedding + BM25 → 写入 Qdrant）");
        return m;
    }

    /** 成本看板：累计 token / 成本 + 最近 N 条 LLM 调用明细（JSON）。 */
    @GetMapping("/metrics/cost")
    public CostMeter.CostSnapshot cost() {
        return costMeter.snapshot();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> badRequest(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }
}
