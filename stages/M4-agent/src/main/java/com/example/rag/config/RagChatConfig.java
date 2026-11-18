package com.example.rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Chat 客户端装配。
 *
 * 背景：本工作空间（sk-ws-）的 chat 模型（如 qwen3.8-27b）只暴露在百炼的
 * OpenAI 兼容端点 /compatible-mode/v1/chat/completions；而 spring-ai-alibaba 的
 * DashScopeChatModel 走的是百炼原生接口 /api/v1/services/aigc/text-generation/generation，
 * 该接口不认工作空间 chat 模型，会返回 "url error"。
 *
 * 因此这里用 Spring AI 的通用 OpenAI 客户端（spring-ai-starter-model-openai）指向兼容端点，
 * 标记为 @Primary 供 QueryRewriter / AnswerService 注入。Embedding 仍由 spring-ai-alibaba 的原生客户端负责。
 */
@Configuration
public class RagChatConfig {

    @Bean
    @Primary
    public ChatClient ragChatClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.create(openAiChatModel);
    }
}
