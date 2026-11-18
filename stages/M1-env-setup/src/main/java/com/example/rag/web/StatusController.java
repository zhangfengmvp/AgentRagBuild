package com.example.rag.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * M1 环境状态自检页：验证 Qdrant 可达性 + 百炼 DashScope Key 是否已配置。
 * 仅做"环境就绪"校验，不接入向量读写与 LLM 调用（M2 起演进）。
 */
@Controller
public class StatusController {

    @Value("${qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${qdrant.port:6333}")
    private int qdrantPort;

    private final RestClient restClient = RestClient.create();

    @GetMapping("/")
    public String statusPage(Model model) {
        model.addAttribute("qdrantHost", qdrantHost);
        model.addAttribute("qdrantPort", qdrantPort);
        model.addAttribute("dashscopeConfigured", isDashscopeConfigured());
        model.addAttribute("qdrantReachable", isQdrantReachable());
        return "status";
    }

    @GetMapping("/api/status")
    @ResponseBody
    public Map<String, Object> statusJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("app", "m1-env-setup");
        m.put("qdrantHost", qdrantHost);
        m.put("qdrantPort", qdrantPort);
        m.put("dashscopeConfigured", isDashscopeConfigured());
        m.put("qdrantReachable", isQdrantReachable());
        boolean ready = isDashscopeConfigured() && isQdrantReachable();
        m.put("status", ready ? "READY" : "NOT_READY");
        return m;
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
