package com.example.rag.web;

import com.example.rag.agent.AgentOrchestrator;
import com.example.rag.agent.AgentResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST API：/api/agent 触发 Agent 编排，返回完整 AgentResult（问题 + 逐步轨迹 + 最终答案 + 引用）。
 */
@RestController
@RequestMapping("/api")
public class AgentController {

    private final AgentOrchestrator orchestrator;

    public AgentController(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
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

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> badRequest(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }
}
