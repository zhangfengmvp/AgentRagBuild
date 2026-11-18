package com.example.rag.agent;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工具注册表：收集所有 {@link Tool} 实现，按名路由，并适配为 Spring AI 的 {@link ToolCallback}
 * 供手动 ReAct 循环驱动（原生 function calling）。
 */
@Service
public class ToolRegistry {

    private final Map<String, Tool> tools = new HashMap<>();

    public ToolRegistry(List<Tool> toolList) {
        for (Tool t : toolList) {
            tools.put(t.name(), t);
        }
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    public Collection<Tool> all() {
        return tools.values();
    }

    /** 把全部工具适配为 Spring AI 的 ToolCallback 数组（供 ChatClient.toolCallbacks 使用） */
    public ToolCallback[] callbacks() {
        return tools.values().stream().map(ToolRegistry::toCallback).toArray(ToolCallback[]::new);
    }

    private static ToolCallback toCallback(Tool tool) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name(tool.name())
                        .description(tool.description())
                        .inputSchema(tool.inputSchema())
                        .build();
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return ToolMetadata.builder().build();
            }

            @Override
            public String call(String toolInput) {
                return tool.execute(parse(toolInput));
            }
        };
    }

    private static Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
