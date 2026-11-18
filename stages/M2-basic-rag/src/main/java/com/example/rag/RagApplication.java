package com.example.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * M2 应用入口。
 * - @SpringBootApplication：开启组件扫描（com.example.rag 下所有 @Component/@Service/@Controller）+ 自动装配。
 * - @ConfigurationPropertiesScan：让 RagProperties 绑定 application.yml 的 rag.* 配置。
 * Spring AI 的 DashScope（embedding/chat）、Qdrant VectorStore 均通过自动装配注入，无需手写 Bean。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class RagApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }
}
