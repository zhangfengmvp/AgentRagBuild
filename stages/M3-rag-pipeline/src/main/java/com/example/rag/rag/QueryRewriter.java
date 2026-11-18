package com.example.rag.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Query 改写：用 LLM 把用户口语化/简短的问题，改写成更利于「向量语义检索 + 关键词检索」的查询。
 * 例：把"spring 怎么解决循环依赖"扩展为"Spring 循环依赖 三级缓存 singletonFactories 解决机制"。
 * 失败时降级为原问题，保证链路可用。
 */
@Service
public class QueryRewriter {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriter.class);

    private final ChatClient chatClient;

    public QueryRewriter(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    private static final String PROMPT = """
            你是一个 RAG 系统的检索查询优化器。请将用户的问题改写成更利于「向量语义检索 + 关键词检索」的查询：
            - 保留核心实体与技术名词（类名、接口名、术语），必要时补充同义词；
            - 若原问题可拆为多个子问题，用空格把多个子查询连起来；
            - 只输出改写后的查询文本本身，不要任何解释、前缀或引号。
            """;

    public String rewrite(String question) {
        try {
            String rewritten = chatClient.prompt()
                    .system(PROMPT)
                    .user(question)
                    .call()
                    .content();
            if (rewritten == null || rewritten.isBlank()) {
                return question;
            }
            String cleaned = rewritten.trim().replaceAll("^[\"'\n\r]+|[\"'\n\r]+$", "");
            log.info("Query 改写：'{}' → '{}'", question, cleaned);
            return cleaned;
        } catch (Exception e) {
            log.warn("Query 改写失败，降级为原问题：{}", e.getMessage());
            return question;
        }
    }
}
