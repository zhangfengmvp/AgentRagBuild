package com.example.rag.web;

import com.example.rag.agent.AgentEventSink;
import com.example.rag.agent.AgentOrchestrator;
import com.example.rag.agent.AgentResult;
import com.example.rag.agent.AgentStep;
import com.example.rag.agent.SemanticCache;
import com.example.rag.rag.Citation;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * REST API：/api/agent 触发 Agent 编排，返回完整 AgentResult（问题 + 逐步轨迹 + 最终答案 + 引用）。
 * /api/agent/stream 为 GET SSE 流式端点（EventSource 直连）。
 */
@RestController
@RequestMapping("/api")
public class AgentController {

    private final AgentOrchestrator orchestrator;
    private final TaskExecutor agentTaskExecutor;
    private final SemanticCache semanticCache;

    public AgentController(AgentOrchestrator orchestrator, TaskExecutor agentTaskExecutor, SemanticCache semanticCache) {
        this.orchestrator = orchestrator;
        this.agentTaskExecutor = agentTaskExecutor;
        this.semanticCache = semanticCache;
    }

    /** SSE 收尾元信息：引用溯源 + 是否命中语义缓存（前端据此渲染侧栏与审计条）。 */
    public record StreamMeta(List<Citation> citations, boolean cacheHit) {
    }

    public record AgentRequest(String question) {
    }

    @PostMapping("/agent")
    public AgentResult agent(@RequestBody AgentRequest req) {
        if (req == null || req.question() == null || req.question().isBlank()) {
            throw new IllegalArgumentException("question 不能为空");
        }
        return orchestrator.run(req.question().trim());
    }

    /**
     * 流式 Agent：GET 便于前端 EventSource 直连。立即返回 SseEmitter，后台线程驱动
     * AgentOrchestrator.runStream，把步骤事件与最终答案 token 实时推给前端。
     */
    @GetMapping("/agent/stream")
    public SseEmitter agentStream(@RequestParam("question") String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question 不能为空");
        }
        String q = question.trim();
        SseEmitter emitter = new SseEmitter(180_000L);
        AgentResult cached = semanticCache.get(q);
        if (cached != null) {
            // 语义缓存命中：直接回放轨迹 + 答案（不重复检索/生成、不计成本）。
            agentTaskExecutor.execute(() -> replay(cached, emitter));
            return emitter;
        }
        agentTaskExecutor.execute(() -> {
            AgentEventSink sink = new SseEmitterSink(emitter);
            orchestrator.runStream(q, sink, result -> {
                try {
                    emitter.send(SseEmitter.event().name("meta")
                            .data(new StreamMeta(result.citations(), false)));
                } catch (IOException ignored) {
                    // 客户端已断开，忽略
                }
                semanticCache.put(q, result);
            });
        });
        return emitter;
    }

    /** 把缓存的 AgentResult 以 SSE 事件回放（步骤 + 答案 token + meta + done）。 */
    private void replay(AgentResult r, SseEmitter emitter) {
        AgentEventSink sink = new SseEmitterSink(emitter);
        try {
            for (AgentStep step : r.steps()) {
                sink.emitStep(step);
            }
            String fa = r.finalAnswer() == null ? "" : r.finalAnswer();
            int i = 0;
            while (i < fa.length()) {
                int end = Math.min(i + 4, fa.length());
                sink.emitToken(fa.substring(i, end));
                i = end;
            }
            try {
                emitter.send(SseEmitter.event().name("meta")
                        .data(new StreamMeta(r.citations(), true)));
            } catch (IOException ignored) {
                // 客户端已断开，忽略
            }
            sink.complete();
        } catch (Throwable t) {
            sink.error(t);
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> badRequest(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }
}
