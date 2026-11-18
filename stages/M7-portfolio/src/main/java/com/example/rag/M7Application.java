package com.example.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * M7 应用入口（作品集整合里程碑）。
 * - 以 M5-engineering 为最完整基线，收拢 M1–M6 全部能力为一个可面试、可分享的生产级作品集。
 * - @SpringBootApplication：开启组件扫描（com.example.rag 下所有 @Component/@Service/@Controller）+ 自动装配。
 * - @ConfigurationPropertiesScan：让 RagProperties 绑定 application.yml 的 rag.* 配置。
 * Spring AI 的 DashScope（embedding）、OpenAI（chat）、Qdrant VectorStore 均通过自动装配注入，无需手写 Bean。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class M7Application {

    public static void main(String[] args) {
        SpringApplication.run(M7Application.class, args);
    }
}
