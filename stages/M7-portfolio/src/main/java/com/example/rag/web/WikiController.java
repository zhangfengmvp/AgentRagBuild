package com.example.rag.web;

import com.example.rag.wiki.KnowledgeGraph;
import com.example.rag.wiki.WikiPage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Wiki 只读查询端点：页面树 / 单页详情 / 搜索。所有查询走内存图谱，无额外 IO。
 */
@RestController
@RequestMapping("/api/wiki")
public class WikiController {

    private final KnowledgeGraph graph;

    public WikiController(KnowledgeGraph graph) {
        this.graph = graph;
    }

    /** 页面树：分类（corpus 子目录）→ 实体轻量视图。 */
    @GetMapping("/tree")
    public Map<String, List<KnowledgeGraph.WikiPageLite>> tree() {
        return graph.tree();
    }

    /** 单实体 WikiPage 详情（含出边/入边与来源）。 */
    @GetMapping("/page")
    public WikiPage page(@RequestParam("name") String name) {
        return graph.page(name);
    }

    /** 实体名/摘要模糊搜索。 */
    @GetMapping("/search")
    public List<KnowledgeGraph.WikiPageLite> search(@RequestParam("q") String q) {
        return graph.search(q);
    }
}
