package com.example.rag.wiki;

import com.example.rag.metrics.CostMeter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

/**
 * 基于 LLM 的实体/关系抽取器（双 RAGAS 中 LLM-judge 同款思路：约束 JSON 输出 + 健壮解析）。
 *
 * <p>抽取在「节」粒度进行（而非切片），以控制 LLM 调用次数与成本；token 用量经
 * {@link CostMeter#record(ChatResponse, String)} 计入成本看板（stage="wiki-extract"）。
 * 任何失败都降级为空结果，绝不阻断主 RAG 入库。
 */
@Service
public class LlmEntityExtractor implements EntityExtractor {

    private static final Logger log = LoggerFactory.getLogger(LlmEntityExtractor.class);

    private final ChatClient chatClient;
    private final CostMeter costMeter;
    private final ObjectMapper om = new ObjectMapper();

    private static final String PROMPT = """
            你是一个严谨的技术知识图谱抽取器。给定一篇文档的【标题】与【正文】，请抽取其中的核心实体（概念/技术/组件/模式）以及它们之间的关系。

            要求：
            1. 实体：只抽取对理解该主题真正重要的实体（3~12 个），每个给出类型（概念/技术/组件/模式之一）与一句话摘要。
            2. 关系：抽取实体之间的有向关系，形如「A -关系-> B」（如「Spring IoC -解决-> 循环依赖」）。关系要具体、可解释。
            3. 实体名称请使用文档中出现的规范表述，不要自行造词。
            4. 仅使用正文中确实出现或被隐含支撑的内容，不要编造。

            【标题】
            %s

            【正文】
            %s

            仅输出如下 JSON，不要输出任何额外文字：
            {"entities":[{"name":"","type":"","summary":""}],"relations":[{"subject":"","relation":"","object":""}]}
            """;

    public LlmEntityExtractor(ChatClient chatClient, CostMeter costMeter) {
        this.chatClient = chatClient;
        this.costMeter = costMeter;
    }

    @Override
    public ExtractionResult extract(String heading, String text) {
        ExtractionResult empty = new ExtractionResult();
        if (text == null || text.isBlank()) {
            return empty;
        }
        try {
            ChatResponse resp = chatClient.prompt()
                    .user(String.format(PROMPT, heading == null ? "" : heading, text))
                    .call()
                    .chatResponse();
            costMeter.record(resp, "wiki-extract");
            String raw = resp.getResult().getOutput().getText();
            return parse(raw);
        } catch (Throwable t) {
            log.warn("Wiki 实体抽取失败（跳过该节，不影响主入库）：{}", t.getMessage());
            return empty;
        }
    }

    private ExtractionResult parse(String raw) {
        ExtractionResult result = new ExtractionResult();
        try {
            String json = extractJson(raw);
            JsonNode root = om.readTree(json);

            JsonNode ents = root.path("entities");
            if (ents.isArray()) {
                for (JsonNode n : ents) {
                    String name = n.path("name").asText("").trim();
                    if (name.isEmpty()) continue;
                    result.getEntities().add(new ExtractionResult.ExtractedEntity(
                            name, n.path("type").asText(""), n.path("summary").asText("")));
                }
            }
            JsonNode rels = root.path("relations");
            if (rels.isArray()) {
                for (JsonNode n : rels) {
                    String s = n.path("subject").asText("").trim();
                    String rel = n.path("relation").asText("").trim();
                    String o = n.path("object").asText("").trim();
                    if (s.isEmpty() || o.isEmpty() || rel.isEmpty()) continue;
                    result.getRelations().add(new Relation(s, rel, o));
                }
            }
        } catch (Exception e) {
            log.warn("Wiki 抽取结果解析失败：{}", e.getMessage());
        }
        return result;
    }

    /** 抽取 JSON 主体：剥离 ```json 围栏，取首个 { 到最后一个 }。 */
    private static String extractJson(String text) {
        if (text == null) return "{}";
        String t = text.trim();
        if (t.startsWith("```")) {
            int first = t.indexOf('{');
            int last = t.lastIndexOf('}');
            if (first >= 0 && last > first) return t.substring(first, last + 1);
        }
        int first = t.indexOf('{');
        int last = t.lastIndexOf('}');
        if (first >= 0 && last > first) return t.substring(first, last + 1);
        return t;
    }
}
