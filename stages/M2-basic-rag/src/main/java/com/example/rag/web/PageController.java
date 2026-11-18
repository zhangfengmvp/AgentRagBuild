package com.example.rag.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestClient;

/**
 * 页面路由：/ 问答页（M2 主入口）；/status 环境状态自检页（沿用 M1）。
 */
@Controller
public class PageController {

    @Value("${spring.ai.vectorstore.qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${rag.qdrant-rest-port:6333}")
    private int qdrantPort;

    private final RestClient restClient = RestClient.create();

    @GetMapping("/")
    public String askPage() {
        return "ask";
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
