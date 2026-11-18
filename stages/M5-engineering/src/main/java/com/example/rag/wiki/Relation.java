package com.example.rag.wiki;

/**
 * 知识图谱中的一条有向关系边：from --relation--> to。
 * 同时作为 LLM 抽取结果的三元组（subject, relation, object）。
 */
public record Relation(String from, String relation, String to) {
}
