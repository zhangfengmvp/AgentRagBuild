package com.example.rag.web;

import com.example.rag.agent.AgentOrchestrator;
import com.example.rag.agent.AgentResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;

/**
 * 页面路由：/ 统一门户首页（M7 作品集入口）；/ask 增强问答页；/agent Agent 编排可视化页；/stream-agent 流式问答；
 * /wiki LLM Wiki 知识库；/status 环境状态自检页。
 */
@Controller
public class PageController {

    @Value("${spring.ai.vectorstore.qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${rag.qdrant-rest-port:6333}")
    private int qdrantPort;

    private final RestClient restClient = RestClient.create();
    private final AgentOrchestrator agentOrchestrator;

    public PageController(AgentOrchestrator agentOrchestrator) {
        this.agentOrchestrator = agentOrchestrator;
    }

    @GetMapping("/")
    public String portalPage() {
        return "index";
    }

    @GetMapping("/ask")
    public String askPage() {
        return "ask";
    }

    @GetMapping("/agent")
    public String agentPage() {
        return "agent";
    }

    @GetMapping("/stream-agent")
    public String streamAgentPage() {
        return "stream-agent";
    }

    @GetMapping("/wiki")
    public String wikiPage() {
        return "wiki";
    }

    @PostMapping("/agent")
    public String agentRun(@RequestParam("question") String question, Model model) {
        AgentResult result = agentOrchestrator.run(question);
        model.addAttribute("question", question);
        model.addAttribute("result", result);
        return "agent";
    }

    @GetMapping("/status")
    public String statusPage(Model model) {
        model.addAttribute("qdrantHost", qdrantHost);
        model.addAttribute("qdrantPort", qdrantPort);
        model.addAttribute("dashscopeConfigured", isDashscopeConfigured());
        model.addAttribute("qdrantReachable", isQdrantReachable());
        return "status";
    }

    private boolean isDashscopeConfigured() {
        String key = System.getenv("DASHSCOPE_API_KEY");
        return key != null && !key.isBlank();
    }

    private boolean isQdrantReachable() {
        try {
            restClient.get()
                    .uri("http://" + qdrantHost + ":" + qdrantPort + "/")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
