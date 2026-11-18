package com.example.rag.web;

import com.example.rag.eval.LLMJudgeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * RAGAS 代理评估端点（Java 侧 LLM-as-judge）。
 * POST /api/eval 评估单次问答；POST /api/eval/batch 批量；GET /api/eval/recent 看最近评估表。
 * 评估为旁路：失败只返回 error 字段，不影响主问答链路。
 */
@RestController
@RequestMapping("/api/eval")
public class EvalController {

    private final LLMJudgeService judge;

    public EvalController(LLMJudgeService judge) {
        this.judge = judge;
    }

    /** 单次评估请求体：问题 + 检索上下文列表 + 待评估答案。 */
    public record EvalRequest(String question, List<String> contexts, String answer) {
    }

    @PostMapping
    public LLMJudgeService.EvalResult eval(@RequestBody EvalRequest req) {
        if (req == null || req.question() == null || req.question().isBlank()) {
            throw new IllegalArgumentException("question 不能为空");
        }
        return judge.evaluate(req.question(), req.contexts(), req.answer());
    }

    @PostMapping("/batch")
    public List<LLMJudgeService.EvalResult> evalBatch(
            @RequestBody List<LLMJudgeService.EvalInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("批量评估输入不能为空");
        }
        return judge.evaluateBatch(inputs);
    }

    @GetMapping("/recent")
    public List<LLMJudgeService.EvalRecord> recent() {
        return judge.recent();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> badRequest(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }
}
