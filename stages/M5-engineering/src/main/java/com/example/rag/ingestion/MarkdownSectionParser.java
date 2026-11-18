package com.example.rag.ingestion;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 按 Markdown 标题（# 开头的行）把文档切分为"节"：heading + 该节正文。
 * M3 保持最简单的结构化解析；解析质量本身是 RAG 效果的隐形天花板。
 */
@Component
public class MarkdownSectionParser {

    public record Section(String heading, String content) {
    }

    public List<Section> parse(String markdown) {
        List<Section> sections = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        // 以离当前行最近的标题作为该段内容的归属；首个标题之前的正文归到「文档开头」
        String current = "（文档开头）";

        for (String line : markdown.split("\\R")) {
            if (line.startsWith("#")) {
                if (!buf.isEmpty()) {
                    sections.add(new Section(current, buf.toString().trim()));
                    buf.setLength(0);
                }
                current = line.replaceAll("^#+\\s*", "").trim();
            } else {
                buf.append(line).append('\n');
            }
        }
        if (!buf.isEmpty()) {
            sections.add(new Section(current, buf.toString().trim()));
        }
        return sections;
    }
}
