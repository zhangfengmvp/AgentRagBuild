package com.example.rag.agent;

import com.example.rag.rag.Citation;
import com.example.rag.rag.PipelineResult;
import com.example.rag.rag.PipelineService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * T1 知识库检索工具（核心工具）：包装 M3 的 PipelineService，完成
 * 改写 → 混合检索(RRF) → Rerank → 生成，并把结果压缩为"答案摘要 + 引用"文本返回给 Agent。
 * 同时通过 ThreadLocal 把本次调用的引用透传给编排器，供最终答案统一展示。
 */
@Service
public class KnowledgeSearchTool implements Tool {

    private final PipelineService pipelineService;
    private final ThreadLocal<List<Citation>> citationsHolder = ThreadLocal.withInitial(ArrayList::new);

    public KnowledgeSearchTool(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @Override
    public String name() {
        return "knowledgeSearch";
    }

    @Override
    public String description() {
        return "在 Java 技术知识库中检索用户问题相关的资料片段，返回答案摘要与引用来源。" +
                "当用户问及 Spring / 分布式 / 数据库 / 缓存 / 事务 / 幂等等 Java 技术主题时使用。";
    }

    @Override
    public String inputSchema() {
        return """
                {"type":"object","properties":{"question":{"type":"string","description":"要检索的 Java 技术问题"}},"required":["question"]}""";
    }

    @Override
    public String execute(Map<String, Object> args) {
        String q = String.valueOf(args.getOrDefault("question", "")).trim();
        PipelineResult pr = pipelineService.run(q);
        citationsHolder.get().addAll(pr.citations());

        StringBuilder sb = new StringBuilder();
        sb.append("【检索问题】").append(pr.originalQuery()).append("\n");
        if (pr.rewrittenQuery() != null && !pr.rewrittenQuery().equals(pr.originalQuery())) {
            sb.append("【改写后】").append(pr.rewrittenQuery()).append("\n");
        }
        sb.append("【答案摘要】\n").append(pr.answer()).append("\n");
        sb.append("【引用来源】\n");
        for (Citation c : pr.citations()) {
            sb.append("- [").append(c.index()).append("] ").append(c.source())
                    .append(" / ").append(c.heading()).append("\n");
        }
        return sb.toString();
    }

    /** 编排器在每次 knowledgeSearch 后取走并清空本次累积的引用 */
    public List<Citation> drainCitations() {
        List<Citation> copy = new ArrayList<>(citationsHolder.get());
        citationsHolder.remove();
        return copy;
    }
}
