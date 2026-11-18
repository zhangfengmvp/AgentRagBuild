package com.example.rag.rag;

import com.example.rag.metrics.CostMeter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 生成：把给定的检索片段拼成带编号的上下文，约束模型"只依据资料回答并标注引用"。
 * 本服务只负责「依据资料生成」，检索/重排由 PipelineService 编排后把命中传入。
 * 生成阶段的 LLM token 用量经 {@link CostMeter} 累计（成本看板 / 可观测）。
 */
@Service
public class AnswerService {

    private final ChatClient chatClient;
    private final CostMeter costMeter;

    public AnswerService(ChatClient chatClient, CostMeter costMeter) {
        this.chatClient = chatClient;
        this.costMeter = costMeter;
    }

    public record Answer(String answer, List<Citation> citations) {
    }

    // 检索增强核心约束：仅依据下方 context 作答、强制用 [n] 标注引用、资料不足时明说，避免模型编造
    private static final String PROMPT = """
            你是一个 Java 技术知识助手。请仅根据下面的参考资料回答问题。
            规则：
            1. 用中文回答，简洁、准确、面向有经验的 Java 工程师；
            2. 引用来源时用 [1]、[2] 标注（对应资料编号）；
            3. 若参考资料不足以回答，请直接说明"参考资料中没有足够内容"，不要编造。

            参考资料：
            {context}

            问题：{question}
            """;

    public Answer generate(String question, List<Hit> hits) {
        if (hits == null || hits.isEmpty()) {
            return new Answer("知识库中没有检索到相关内容（可先点击页面上的「入库语料」，或换个问法）。", List.of());
        }

        StringBuilder context = new StringBuilder();
        List<Citation> citations = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            Hit h = hits.get(i);
            context.append("[").append(i + 1).append("] (").append(h.source()).append("#").append(h.heading()).append(")\n")
                    .append(h.text()).append("\n\n");
            citations.add(new Citation(i + 1, h.source(), h.heading(), h.score()));
        }

        ChatResponse resp = chatClient.prompt()
                .user(u -> u.text(PROMPT)
                        .param("context", context.toString())
                        .param("question", question))
                .call()
                .chatResponse();
        costMeter.record(resp, "generate");
        String answer = resp.getResult().getOutput().getText();
        return new Answer(answer, citations);
    }
}
