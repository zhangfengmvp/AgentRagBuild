package com.example.rag.metrics;

import com.example.rag.config.RagProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 成本看板 + 可观测核心组件。
 *
 * <p>职责：
 * <ol>
 *   <li>按每次 LLM 响应的 token 用量（prompt / completion）× 可配置单价累计成本；</li>
 *   <li>维护最近 N 条 LLM 调用明细（时间/阶段/tokens/成本），供 {@code /api/metrics/cost} 查询；</li>
 *   <li>提供 Micrometer 包裹器：管道各阶段 {@link #timed(String, Runnable)} 计时、工具调用 / Agent 步数 {@link Counter}；</li>
 *   <li>所有指标可经 actuator/Prometheus 暴露（{@code /actuator/prometheus}）。</li>
 * </ol>
 *
 * <p>线程安全：累计量用 {@link AtomicLong}，最近明细用并发队列。</p>
 */
@Component
public class CostMeter {

    private final double inputPricePer1k;
    private final double outputPricePer1k;
    private final MeterRegistry registry;

    private final AtomicLong totalPromptTokens = new AtomicLong();
    private final AtomicLong totalCompletionTokens = new AtomicLong();
    private final AtomicLong totalTokens = new AtomicLong();
    private final AtomicLong totalCalls = new AtomicLong();

    private final Counter toolCallCounter;
    private final Counter agentStepCounter;

    private final Queue<RecentCall> recent = new ConcurrentLinkedQueue<>();
    private static final int MAX_RECENT = 50;

    public CostMeter(RagProperties props, MeterRegistry registry) {
        RagProperties.Cost cost = props.cost();
        this.inputPricePer1k = cost.inputPricePer1k();
        this.outputPricePer1k = cost.outputPricePer1k();
        this.registry = registry;
        this.toolCallCounter = Counter.builder("rag.agent.tool.calls")
                .description("Agent 工具调用总次数").register(registry);
        this.agentStepCounter = Counter.builder("rag.agent.steps")
                .description("Agent 推理步数").register(registry);
    }

    /** 从 ChatResponse 提取 usage 并记录 token 成本（stage 仅用于明细标注，不作为指标 tag）。 */
    public void record(ChatResponse resp, String stage) {
        if (resp == null) {
            return;
        }
        ChatResponseMetadata meta = resp.getMetadata();
        if (meta == null) {
            return;
        }
        Usage usage = meta.getUsage();
        if (usage == null) {
            return;
        }
        record(safe(usage.getPromptTokens()), safe(usage.getCompletionTokens()), stage);
    }

    /** 直接以 token 数记录（拿不到 ChatResponse 时使用，如 embedding 估算）。 */
    public void record(long promptTokens, long completionTokens, String stage) {
        if (promptTokens <= 0 && completionTokens <= 0) {
            return;
        }
        totalPromptTokens.addAndGet(promptTokens);
        totalCompletionTokens.addAndGet(completionTokens);
        totalTokens.addAndGet(promptTokens + completionTokens);
        totalCalls.incrementAndGet();
        double cost = promptTokens / 1000.0 * inputPricePer1k + completionTokens / 1000.0 * outputPricePer1k;
        recent.add(new RecentCall(Instant.now().toString(), stage, promptTokens, completionTokens, round(cost)));
        while (recent.size() > MAX_RECENT) {
            recent.poll();
        }
    }

    public void countToolCall() {
        toolCallCounter.increment();
    }

    public void countAgentStep() {
        agentStepCounter.increment();
    }

    /** 包裹一个阶段的执行并计时（无返回值版）。 */
    public void timed(String stage, Runnable work) {
        timer(stage).record(work);
    }

    /** 包裹一个阶段的执行并计时（有返回值版）。 */
    public <T> T timed(String stage, java.util.function.Supplier<T> work) {
        return timer(stage).record(work);
    }

    private Timer timer(String stage) {
        return Timer.builder("rag.pipeline.stage")
                .tag("stage", stage)
                .description("RAG 管道各阶段耗时（毫秒）")
                .register(registry);
    }

    /** 当前累计快照（供 /api/metrics/cost 返回 JSON）。 */
    public CostSnapshot snapshot() {
        double totalCost = totalPromptTokens.get() / 1000.0 * inputPricePer1k
                + totalCompletionTokens.get() / 1000.0 * outputPricePer1k;
        return new CostSnapshot(
                totalPromptTokens.get(),
                totalCompletionTokens.get(),
                totalTokens.get(),
                totalCalls.get(),
                round(totalCost),
                round(inputPricePer1k),
                round(outputPricePer1k),
                List.copyOf(recent));
    }

    private static long safe(Number n) {
        return n == null ? 0 : n.longValue();
    }

    private static double round(double d) {
        return Math.round(d * 100000.0) / 100000.0;
    }

    /** 单次 LLM 调用明细。 */
    public record RecentCall(String time, String stage, long promptTokens, long completionTokens, double cost) {
    }

    /** 成本看板快照。 */
    public record CostSnapshot(long totalPromptTokens, long totalCompletionTokens, long totalTokens,
                               long totalCalls, double totalCost, double inputPricePer1k,
                               double outputPricePer1k, List<RecentCall> recent) {
    }
}
