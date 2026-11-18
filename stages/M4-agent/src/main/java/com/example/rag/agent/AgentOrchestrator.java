package com.example.rag.agent;

import com.example.rag.config.RagProperties;
import com.example.rag.rag.Citation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 编排器：手动 ReAct 循环（思考 → 行动 → 观察）。
 *
 * 为什么手动而非交给 Spring AI 自动工具调用：
 * 1) 能把每一轮"思考/行动/观察"逐条落进 {@link AgentStep}，供前端时间线完整可视化（自动模式难以捕获中间轨迹）；
 * 2) 对 OpenAI 兼容端点的工具调用兼容性更可控。
 *
 * 流程：维护 messages 列表，每轮把当前对话交给 ChatClient（声明全部工具）；
 * 若模型返回 tool_calls 则逐一执行、把结果作为 ToolResponseMessage 回灌后继续循环；
 * 若返回纯文本则视为最终答案，结束。循环设最大轮次防止失控。
 */
@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private static final String SYSTEM_PROMPT = """
            你是一个严谨的 Java 技术知识助手 Agent。你的回答必须基于工具检索到的资料，你的回答至少要经历一次工具调用，不得凭自身记忆编造。

            可用工具：
            - knowledgeSearch：在 Java 技术知识库中检索资料（Spring / 分布式 / 数据库 / 缓存 / 事务 / 幂等等）。
            - codeSnippetSearch：按主题获取 Spring / 分布式相关的示例代码（如 spring-transaction / redis-distributed-lock / idempotency）。

            工作流程：
            1. 先思考需要哪些资料；
            2. 调用 knowledgeSearch（必要时 codeSnippetSearch）获取真实资料，再综合作答；当用户需要代码示例/实现时，调用 codeSnippetSearch 获取，禁止自行编造代码；
            3. 综合工具返回的资料用中文作答，并标注引用（如 [1][2] 对应知识库引用来源）；
            4. 若工具未返回相关资料，请如实说明，不要编造。""";

    private final ChatClient chatClient;
    private final ToolRegistry toolRegistry;
    private final int maxSteps;
    private final boolean reactFallback;

    public AgentOrchestrator(ChatClient chatClient, ToolRegistry toolRegistry, RagProperties props) {
        this.chatClient = chatClient;
        this.toolRegistry = toolRegistry;
        this.maxSteps = props.agentMaxSteps();
        this.reactFallback = props.agentReactFallback();
    }

    public AgentResult run(String question) {
        if (reactFallback) {
            log.warn("agent.react-fallback=true：走「强制首步双工具」旧模式（已验证稳定）。");
        }

        List<Message> conv = new ArrayList<>();
        conv.add(new SystemMessage(SYSTEM_PROMPT));
        conv.add(new UserMessage(question));

        List<AgentStep> steps = new ArrayList<>();
        List<Citation> citations = new ArrayList<>();
        String finalAnswer = null;
        int step = 0;
        boolean usedTool = false;

        // react-fallback 旧模式：首步强制 knowledgeSearch（用户要代码时再强制 codeSnippetSearch）。
        // 默认（false）不强制，交给模型自主 ReAct，由下方护栏保证答案扎根语料。
        if (reactFallback) {
            usedTool = forceToolCall("knowledgeSearch",
                    jackson(java.util.Map.of("question", question)), conv, steps, citations);
            if (needsCode(question)) {
                usedTool = forceToolCall("codeSnippetSearch",
                        jackson(java.util.Map.of("topic", detectTopic(question))), conv, steps, citations) || usedTool;
            }
        }

        // 主循环：模型可自主调用工具，或由护栏在"偷懒"时结构性强制一次真实检索。
        while (step < maxSteps) {
            step++;
            ChatResponse resp = chatClient.prompt()
                    .messages(conv)
                    .options(OpenAiChatOptions.builder()
                            .toolCallbacks(toolRegistry.callbacks())
                            .internalToolExecutionEnabled(false) // 关键：禁止框架内部执行工具，tool_calls 原样透出给手动 ReAct 循环
                            .build())
                    .call()
                    .chatResponse();
            AssistantMessage am = (AssistantMessage) resp.getResult().getOutput();

            List<AssistantMessage.ToolCall> calls = am.getToolCalls();
            if (calls == null || calls.isEmpty()) {
                // 模型本轮未调用任何工具就试图作答
                if (!usedTool && !reactFallback) {
                    // 扎根护栏（结构性强制）：模型跳过真实检索 → 强制一次 knowledgeSearch 后再让其作答。
                    // 是真实调用而非文本提醒，可杜绝"幻觉式谎称已调用工具"导致的脱离语料作答。
                    if (forceToolCall("knowledgeSearch",
                            jackson(java.util.Map.of("question", question)), conv, steps, citations)) {
                        usedTool = true;
                    }
                    continue; // 回到循环，模型必须基于真实检索结果作答
                }
                // usedTool 已为真（模型自主调用或护栏触发），正常采纳为最终答案
                finalAnswer = am.getText();
                break;
            }

            // 本轮模型确实发出了真实工具调用
            usedTool = true;
            // 若模型同时给出思考文本，记为 THOUGHT（最终答案轮不再重复记 THOUGHT）
            if (am.getText() != null && !am.getText().isBlank()) {
                steps.add(new AgentStep("THOUGHT", null, null, am.getText(), System.currentTimeMillis()));
            }

            for (AssistantMessage.ToolCall tc : calls) {
                steps.add(new AgentStep("ACTION", tc.name(), tc.arguments(), null, System.currentTimeMillis()));
                String obs = executeTool(tc, citations);
                steps.add(new AgentStep("OBSERVATION", tc.name(), null, obs, System.currentTimeMillis()));
                ToolResponseMessage.ToolResponse tr =
                        new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), obs);
                conv.add(new ToolResponseMessage(List.of(tr)));
            }
        }

        if (finalAnswer == null) {
            finalAnswer = "（Agent 在 " + maxSteps + " 步内未收敛，请调整问题或放宽 rag.agent-max-steps。）";
        }
        steps.add(new AgentStep("FINAL", null, null, finalAnswer, System.currentTimeMillis()));

        // 引用去重：同一片段（source+heading）可能从「稠密命中」与「重排命中」各计入一次，
        // 按 source+heading 去重，保留首次出现者（同片段在引用面板只列一次）。
        Map<String, Citation> seen = new LinkedHashMap<>();
        for (Citation c : citations) {
            seen.putIfAbsent(c.source() + "#" + c.heading(), c);
        }
        return new AgentResult(question, steps, finalAnswer, new ArrayList<>(seen.values()));
    }

    /**
     * 程序化触发一次工具调用，把"工具调用 + 结果"作为真实消息注入对话，
     * 保证检索/取码真实发生（避免模型幻觉式谎称已调用工具导致答案脱离语料/凭空编造代码）。
     */
    private boolean forceToolCall(String toolName, String argsJson, List<Message> conv,
                                  List<AgentStep> steps, List<Citation> citations) {
        Tool tool = toolRegistry.get(toolName);
        if (tool == null) {
            return false;
        }
        try {
            steps.add(new AgentStep("ACTION", toolName, argsJson, null, System.currentTimeMillis()));
            String obs = tool.execute(parseArgs(argsJson));
            steps.add(new AgentStep("OBSERVATION", toolName, null, obs, System.currentTimeMillis()));
            if (tool instanceof KnowledgeSearchTool kst) {
                citations.addAll(kst.drainCitations());
            }
            String callId = "force-" + toolName + "-1";
            AssistantMessage.ToolCall forcedCall =
                    new AssistantMessage.ToolCall(callId, "function", toolName, argsJson);
            conv.add(new AssistantMessage("", java.util.Map.of(), java.util.List.of(forcedCall)));
            conv.add(new ToolResponseMessage(java.util.List.of(
                    new ToolResponseMessage.ToolResponse(callId, toolName, obs))));
            return true;
        } catch (Exception e) {
            log.warn("强制 {} 失败：{}", toolName, e.getMessage());
            return false;
        }
    }

    /** 用户问题是否涉及代码示例需求 */
    private boolean needsCode(String q) {
        return q != null && (q.contains("代码") || q.contains("示例") || q.contains("code")
                || q.contains("实现") || q.contains("片段"));
    }

    /** 从问题中粗略识别代码主题，映射到 codeSnippetSearch 的可用主题 */
    private String detectTopic(String q) {
        String s = q.toLowerCase();
        if (s.contains("redis")) return "redis-distributed-lock";
        if (s.contains("事务") || s.contains("transaction")) return "spring-transaction";
        if (s.contains("幂等") || s.contains("重复提交") || s.contains("idempot")) return "idempotency";
        return "redis-distributed-lock";
    }

    private String jackson(java.util.Map<String, String> m) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(m);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String executeTool(AssistantMessage.ToolCall tc, List<Citation> citations) {
        try {
            Tool tool = toolRegistry.get(tc.name());
            if (tool == null) {
                return "未知工具：" + tc.name();
            }
            String result = tool.execute(parseArgs(tc.arguments()));
            if (tool instanceof KnowledgeSearchTool kst) {
                citations.addAll(kst.drainCitations());
            }
            return result;
        } catch (Exception e) {
            log.warn("工具 {} 执行失败：{}", tc.name(), e.getMessage());
            return "工具执行异常：" + e.getMessage();
        }
    }

    private Map<String, Object> parseArgs(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}
