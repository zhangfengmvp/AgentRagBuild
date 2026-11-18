package com.example.rag.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 生成：把检索到的片段拼成带编号的上下文，约束模型"只依据资料回答并标注引用"。
 */
@Service
public class AnswerService {

    private final ChatClient chatClient;
    private final SearchService searchService;

    public AnswerService(ChatClient chatClient, SearchService searchService) {
        this.chatClient = chatClient;
        this.searchService = searchService;
    }

    public record Citation(int index, String source, String heading, Double score) {
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

    public Answer ask(String question) {
        List<Document> docs = searchService.search(question);
        if (docs == null || docs.isEmpty()) {
            return new Answer("知识库中没有检索到相关内容（可先点击页面上的「入库语料」，或换个问法）。", List.of());
        }

        StringBuilder context = new StringBuilder();
        List<Citation> citations = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            Document d = docs.get(i);
            String source = String.valueOf(d.getMetadata().getOrDefault("source", "unknown"));
            String heading = String.valueOf(d.getMetadata().getOrDefault("heading", ""));
            context.append("[").append(i + 1).append("] (").append(source).append("#").append(heading).append(")\n")
                    .append(d.getText()).append("\n\n");
            Double score = d.getScore();
            citations.add(new Citation(i + 1, source, heading, score));
        }

        String answer = chatClient.prompt()
                .user(u -> u.text(PROMPT)
                        .param("context", context.toString())
                        .param("question", question))
                .call()
                .content();
        return new Answer(answer, citations);
    }
}
