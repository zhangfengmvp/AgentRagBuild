package com.example.rag.rag;

/**
 * 答案引用：片段编号 + 来源文件 + 标题 + 相似度/重排分。
 */
public record Citation(int index, String source, String heading, Double score) {
}
