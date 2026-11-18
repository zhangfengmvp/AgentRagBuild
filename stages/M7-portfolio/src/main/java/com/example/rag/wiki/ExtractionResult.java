package com.example.rag.wiki;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 对「一个文档节」的抽取结果：实体列表 + 关系三元组列表。
 * 解析失败或模型未返回有效内容时返回空结果（不抛异常，保证入库链路可用）。
 */
public class ExtractionResult {

    /** 抽取出的实体（名称 + 类型 + 摘要）。 */
    public static class ExtractedEntity {
        public String name;
        public String type;
        public String summary;

        public ExtractedEntity() {
        }

        public ExtractedEntity(String name, String type, String summary) {
            this.name = name;
            this.type = type;
            this.summary = summary;
        }
    }

    private List<ExtractedEntity> entities = new ArrayList<>();
    private List<Relation> relations = new ArrayList<>();

    public List<ExtractedEntity> getEntities() {
        return entities;
    }

    public void setEntities(List<ExtractedEntity> entities) {
        this.entities = entities != null ? entities : new ArrayList<>();
    }

    public List<Relation> getRelations() {
        return relations;
    }

    public void setRelations(List<Relation> relations) {
        this.relations = relations != null ? relations : new ArrayList<>();
    }

    public boolean isEmpty() {
        return entities.isEmpty() && relations.isEmpty();
    }
}
