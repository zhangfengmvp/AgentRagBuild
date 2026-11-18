package com.example.rag.eval;

import com.example.rag.metrics.CostMeter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java 侧 RAGAS 代理评估（双 RAGAS 之「轻量即时版」）。
 *
 * <p>用 LLM 作为裁判，对 (问题, 检索上下文, 答案) 三项核心 RAGAS 指标打 0–1 代理分：
 * <ul>
 *   <li>faithfulness（忠实度）：答案是否仅由上下文支撑，无编造；</li>
 *   <li>answer_relevancy（答案相关性）：答案是否切题；</li>
 *   <li>context_relevancy（上下文相关性）：检索到的上下文与问题是否相关。</li>
 * </ul>
 *
 * <p>这是「实时/低成本」代理评估，用于主链路旁路的即时反馈；权威评估请用 Python 侧
 * {@code eval/ragas_eval.py}（调用真 RAGAS 库）。本服务任何失败都降级返回 error 字段，
 * 绝不阻断主问答链路。</p>
 */
@Service
public class LLMJudgeService {

    private static final Logger log = LoggerFactory.getLogger(LLMJudgeService.class);

    private static final String PROMPT = """
            你是一个严格的 RAG 质量评估裁判。根据【用户问题】【检索上下文】【待评估答案】，对三项指标各打一个 0 到 1 之间的小数（保留两位小数，1 为满分）。

            指标定义：
            1) faithfulness（忠实度）：答案是否完全由上下文支撑、无凭空编造。
            2) answer_relevancy（答案相关性）：答案是否准确切中用户问题。
            3) context_relevancy（上下文相关性）：提供的检索上下文是否与问题高度相关、足以支撑答案。

            【用户问题】
            %s

            【检索上下文】
            %s

            【待评估答案】
            %s

            仅输出如下格式的 JSON，不要输出任何额外文字：
            {"faithfulness":0.00,"answerRelevancy":0.00,"contextRelevancy":0.00}
            """;

    private static final Pattern FIELD = Pattern.compile(
            "\"?(faithfulness|answerRelevancy|answer_relevancy|contextRelevancy|context_relevancy)\"?\\s*[:=]\\s*([0-9](\\.\\d+)?)");

    private final ChatClient chatClient;
    private final CostMeter costMeter;
    private final ObjectMapper om = new ObjectMapper();
    private final Counter evalCounter;
    private final Counter evalErrorCounter;
    private final Queue<EvalRecord> recent = new ConcurrentLinkedQueue<>();
    private static final int MAX_RECENT = 100;

    public LLMJudgeService(ChatClient chatClient, CostMeter costMeter, MeterRegistry registry) {
        this.chatClient = chatClient;
        this.costMeter = costMeter;
        this.evalCounter = Counter.builder("rag.eval.calls")
                .description("LLM 评估调用次数").register(registry);
        this.evalErrorCounter = Counter.builder("rag.eval.errors")
                .description("LLM 评估失败次数").register(registry);
    }

    public record Scores(double faithfulness, double answerRelevancy, double contextRelevancy) {
    }

    public record EvalResult(String question, Scores scores, String raw, String error) {
    }

    public record EvalRecord(String time, String question, double faithfulness,
                             double answerRelevancy, double contextRelevancy, String error) {
    }

    /**
     * 评估单次问答。任何异常都降级为带 error 字段的结果，不影响调用方。
     */
    public EvalResult evaluate(String question, List<String> contexts, String answer) {
        evalCounter.increment();
        String ctx = (contexts == null || contexts.isEmpty())
                ? "（无检索上下文）" : String.join("\n\n", contexts);
        String q = question == null ? "" : question;
        String a = answer == null ? "" : answer;
        String prompt = String.format(PROMPT, q, ctx, a);
        try {
            ChatResponse resp = chatClient.prompt().user(prompt).call().chatResponse();
            costMeter.record(resp, "eval");
            String text = resp.getResult().getOutput().getText();
            Scores scores = parseScores(text);
            EvalResult result = new EvalResult(q, scores, text, null);
            store(result);
            return result;
        } catch (Throwable t) {
            evalErrorCounter.increment();
            log.warn("LLM 评估失败：{}", t.getMessage());
            return new EvalResult(q, null, null, t.getMessage());
        }
    }

    /** 批量评估（逐项独立调用，互不影响）。 */
    public List<EvalResult> evaluateBatch(List<EvalInput> inputs) {
        List<EvalResult> out = new ArrayList<>();
        if (inputs != null) {
            for (EvalInput in : inputs) {
                out.add(evaluate(in.question(), in.contexts(), in.answer()));
            }
        }
        return out;
    }

    public record EvalInput(String question, List<String> contexts, String answer) {
    }

    public List<EvalRecord> recent() {
        return new ArrayList<>(recent);
    }

    private void store(EvalResult r) {
        double f = -1, ar = -1, cr = -1;
        if (r.scores() != null) {
            f = r.scores().faithfulness();
            ar = r.scores().answerRelevancy();
            cr = r.scores().contextRelevancy();
        }
        recent.add(new EvalRecord(Instant.now().toString(), r.question(), f, ar, cr, r.error()));
        while (recent.size() > MAX_RECENT) {
            recent.poll();
        }
    }

    private Scores parseScores(String text) {
        if (text == null || text.isBlank()) {
            return new Scores(-1, -1, -1);
        }
        try {
            String json = extractJson(text);
            JsonNode n = om.readTree(json);
            double f = clamp(n.path("faithfulness").asDouble(-1));
            double ar = clamp(n.path("answerRelevancy").asDouble(n.path("answer_relevancy").asDouble(-1)));
            double cr = clamp(n.path("contextRelevancy").asDouble(n.path("context_relevancy").asDouble(-1)));
            return new Scores(f, ar, cr);
        } catch (Exception e) {
            return regexFallback(text);
        }
    }

    private Scores regexFallback(String text) {
        double f = -1, ar = -1, cr = -1;
        Matcher m = FIELD.matcher(text);
        while (m.find()) {
            String key = m.group(1).toLowerCase();
            double val = clamp(Double.parseDouble(m.group(2)));
            if (key.contains("faith")) f = val;
            else if (key.contains("answer")) ar = val;
            else if (key.contains("context")) cr = val;
        }
        return new Scores(f, ar, cr);
    }

    private static String extractJson(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int first = t.indexOf('{');
            int last = t.lastIndexOf('}');
            if (first >= 0 && last > first) {
                return t.substring(first, last + 1);
            }
        }
        int first = t.indexOf('{');
        int last = t.lastIndexOf('}');
        if (first >= 0 && last > first) {
            return t.substring(first, last + 1);
        }
        return t;
    }

    private static double clamp(double v) {
        if (Double.isNaN(v) || v < 0) return -1;
        return Math.min(1.0, Math.round(v * 100.0) / 100.0);
    }
}
