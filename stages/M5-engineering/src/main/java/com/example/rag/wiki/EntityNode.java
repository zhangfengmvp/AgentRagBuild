package com.example.rag.wiki;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识图谱中的一个实体节点（即一篇「Wiki 页面」的底层数据）。
 *
 * <p>区别于普通 RAG 的扁平切片：实体节点是<b>结构化、可互链、能演进</b>的知识单元。
 * 同一实体会被多次入库（增量修订）不断合并关系、追加来源、自增版本。
 */
public class EntityNode {

    /** 归一化 key（小写 + 去空格），作为图谱主键，保证同名实体合并。 */
    private String name;
    /** 展示名（保留原始大小写，如 "Spring IoC"）。 */
    private String displayName;
    /** 实体类型：概念 / 技术 / 组件 / 模式 … */
    private String type;
    /** 由 LLM 抽取并随修订追加多来源的摘要。 */
    private String summary;
    /** 增量修订版本号，首次为 1，每次重入库命中自增。 */
    private int version;
    /** 来源引用列表，形如 "spring/ioc.md#3.2 循环依赖"，去重保存。 */
    private List<String> sources = new ArrayList<>();
    /** 出边（from=本节点）。 */
    private List<Relation> out = new ArrayList<>();

    public EntityNode() {
    }

    public EntityNode(String name, String displayName, String type, String summary) {
        this.name = name;
        this.displayName = displayName;
        this.type = type;
        this.summary = summary;
        this.version = 1;
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
        this.sources = sources != null ? sources : new ArrayList<>();
    }

    public List<Relation> getOut() {
        return out;
    }

    public void setOut(List<Relation> out) {
        this.out = out != null ? out : new ArrayList<>();
    }
}
