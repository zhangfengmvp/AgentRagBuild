package com.example.rag.wiki;

/**
 * 实体/关系抽取器契约。实现可替换为不同模型或规则方案；当前默认实现为 {@link LlmEntityExtractor}。
 */
public interface EntityExtractor {

    /**
     * 对单篇文档「节」抽取实体与关系。
     *
     * @param heading 节标题（可为空）
     * @param text    节正文
     * @return 抽取结果；解析失败或空输入返回空结果（不抛异常，保证入库链路可用）
     */
    ExtractionResult extract(String heading, String text);
}
