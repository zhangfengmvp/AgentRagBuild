package com.example.rag.wiki;

import java.util.ArrayList;
import java.util.List;

/**
 * 对外暴露的「Wiki 页面」聚合视图：供 /api/wiki/page 与前端渲染。
 * 在 {@link EntityNode} 基础上补充了「入边」（谁指向了我），由 KnowledgeGraph 查询时计算。
 */
public class WikiPage {

    private String name;
    private String displayName;
    private String type;
    private String summary;
    private int version;
    private List<String> sources = new ArrayList<>();
    private List<Relation> out = new ArrayList<>();
    private List<Relation> in = new ArrayList<>();

    public WikiPage() {
    }

    public WikiPage(EntityNode node) {
        this.name = node.getName();
        this.displayName = node.getDisplayName();
        this.type = node.getType();
        this.summary = node.getSummary();
        this.version = node.getVersion();
        this.sources = node.getSources();
        this.out = node.getOut();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public List<String> getSources() {
        return sources;
    }

    public void setSources(List<String> sources) {
        this.sources = sources;
    }

    public List<Relation> getOut() {
        return out;
    }

    public void setOut(List<Relation> out) {
        this.out = out;
    }

    public List<Relation> getIn() {
        return in;
    }

    public void setIn(List<Relation> in) {
        this.in = in;
    }
}
