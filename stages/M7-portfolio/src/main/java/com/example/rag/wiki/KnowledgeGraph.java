package com.example.rag.wiki;

import com.example.rag.config.RagProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 知识图谱：内存态实体节点 + 关系边，落盘为本地 {@code wiki/kg.json}（无外部依赖）。
 *
 * <p>核心能力：
 * <ul>
 *   <li><b>构建</b>：{@link #merge(ExtractionResult, String)} 在入库时把 LLM 抽取结果并入图谱；</li>
 *   <li><b>增量修订</b>：重入库时按实体名（归一化）合并——关系取并集、来源去重追加、{@code version++}，
 *       实现"演进而非重建"（这正是 LLM Wiki 区别于普通 RAG 的关键）；</li>
 *   <li><b>查询</b>：{@link #tree()} 页面树、{@link #page(String)} 单页详情、{@link #search(String)} 模糊搜索；</li>
 *   <li><b>持久化</b>：启动 {@link #load()} 加载、入库后/关闭前 {@link #save()} 落盘。</li>
 * </ul>
 *
 * 存储层集中在 {@link #save()}/{@link #load()}，未来换 Neo4j 只需替换这两个方法与节点容器，上层不变。
 */
@Service
public class KnowledgeGraph {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraph.class);

    private final Map<String, EntityNode> nodes = new ConcurrentHashMap<>();
    private final ObjectMapper om = new ObjectMapper();
    private final String storePath;
    private final MeterRegistry registry;

    public KnowledgeGraph(RagProperties props, MeterRegistry registry) {
        this.storePath = props.wiki().storePath();
        this.registry = registry;
        Gauge.builder("rag.wiki.entities", this, g -> (double) g.nodes.size())
                .description("Wiki 实体节点数").register(registry);
        Gauge.builder("rag.wiki.relations", this, g -> (double) g.relationCount())
                .description("Wiki 关系边数").register(registry);
    }

    /** 归一化实体名作为图谱主键（小写 + 去空格），保证同名实体跨次入库合并。 */
    public static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    /** 启动加载：文件不存在则跳过（首次运行）。 */
    @PostConstruct
    public void load() {
        Path p = Path.of(storePath);
        if (!Files.exists(p)) {
            log.info("Wiki 图谱文件不存在（首次运行），跳过加载：{}", storePath);
            return;
        }
        try {
            Store wrapper = om.readValue(Files.readString(p, StandardCharsets.UTF_8), Store.class);
            if (wrapper != null && wrapper.nodes != null) {
                for (EntityNode n : wrapper.nodes) {
                    if (n.getName() != null) nodes.put(n.getName(), n);
                }
                log.info("Wiki 图谱已加载：{} 个实体", nodes.size());
            }
        } catch (Exception e) {
            log.warn("Wiki 图谱加载失败（继续使用空图谱）：{}", e.getMessage());
        }
    }

    /** 关闭前落盘（也由入库成功后显式调用）。 */
    @PreDestroy
    public void saveOnShutdown() {
        try {
            save();
        } catch (Exception ignored) {
            // 关闭阶段忽略
        }
    }

    /** 将当前图谱写入 wiki/kg.json（线程安全：单写）。 */
    public synchronized void save() {
        try {
            Path p = Path.of(storePath);
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            Store wrapper = new Store(Instant.now().toString(), new ArrayList<>(nodes.values()));
            Files.writeString(p, om.writerWithDefaultPrettyPrinter().writeValueAsString(wrapper), StandardCharsets.UTF_8);
            log.info("Wiki 图谱已落盘：{} 个实体 → {}", nodes.size(), storePath);
        } catch (Exception e) {
            log.warn("Wiki 图谱保存失败：{}", e.getMessage());
        }
    }

    /**
     * 增量合并一次抽取结果（入库时调用）。
     *
     * @param result    某「节」的 LLM 抽取结果
     * @param sourceRef 来源引用（如 "spring/ioc.md#3.2 循环依赖"），用于溯源与版本标记
     */
    public synchronized void merge(ExtractionResult result, String sourceRef) {
        if (result == null || result.isEmpty()) {
            return;
        }
        // 1) 实体并入（已存在则版本化 + 来源追加）
        for (ExtractionResult.ExtractedEntity e : result.getEntities()) {
            String key = normalize(e.name);
            if (key.isEmpty()) continue;
            EntityNode node = nodes.get(key);
            if (node == null) {
                node = new EntityNode(key, e.name, e.type, e.summary);
                nodes.put(key, node);
            } else {
                node.setVersion(node.getVersion() + 1);
                if (e.summary != null && !e.summary.isBlank()
                        && (node.getSummary() == null || !node.getSummary().contains(e.summary))) {
                    node.setSummary(e.summary); // 取最新抽取摘要
                }
                if (e.type != null && !e.type.isBlank() && (node.getType() == null || node.getType().isBlank())) {
                    node.setType(e.type);
                }
            }
            if (sourceRef != null && !sourceRef.isBlank() && !node.getSources().contains(sourceRef)) {
                node.getSources().add(sourceRef);
            }
        }
        // 2) 关系并入（from 节点的出边；目标节点若不存在则建占位，保证图谱连通）
        for (Relation r : result.getRelations()) {
            String from = normalize(r.from());
            String to = normalize(r.to());
            if (from.isEmpty() || to.isEmpty() || r.relation() == null || r.relation().isBlank()) continue;
            EntityNode fromNode = nodes.computeIfAbsent(from, k -> new EntityNode(k, r.from(), "", ""));
            nodes.computeIfAbsent(to, k -> new EntityNode(k, r.to(), "", ""));
            boolean exists = fromNode.getOut().stream()
                    .anyMatch(x -> x.relation().equals(r.relation()) && x.to().equals(to));
            if (!exists) {
                fromNode.getOut().add(new Relation(from, r.relation(), to));
            }
        }
    }

    /** 页面树：按来源首段目录（spring/database/...）聚合实体；无来源归入「未分类」。 */
    public Map<String, List<WikiPageLite>> tree() {
        Map<String, List<WikiPageLite>> map = new LinkedHashMap<>();
        for (EntityNode n : nodes.values()) {
            String cat = "未分类";
            if (n.getSources() != null && !n.getSources().isEmpty()) {
                String first = n.getSources().get(0);
                int slash = first.indexOf('/');
                if (slash > 0) cat = first.substring(0, slash);
            }
            map.computeIfAbsent(cat, k -> new ArrayList<>())
                    .add(new WikiPageLite(n.getDisplayName(), n.getType(), n.getVersion()));
        }
        // 每类按展示名排序
        map.forEach((k, v) -> v.sort(Comparator.comparing(WikiPageLite::displayName)));
        return map;
    }

    /** 单实体 WikiPage 详情（含出边与入边）。 */
    public WikiPage page(String name) {
        EntityNode node = nodes.get(normalize(name));
        if (node == null) return null;
        WikiPage wp = new WikiPage(node);
        // 计算入边：扫描所有节点的出边，to == 本节点
        List<Relation> in = new ArrayList<>();
        for (EntityNode other : nodes.values()) {
            for (Relation r : other.getOut()) {
                if (r.to().equals(node.getName())) {
                    in.add(new Relation(other.getName(), r.relation(), node.getName()));
                }
            }
        }
        wp.setIn(in);
        return wp;
    }

    /** 模糊搜索：匹配实体名/摘要（大小写不敏感）。 */
    public List<WikiPageLite> search(String q) {
        if (q == null || q.isBlank()) return new ArrayList<>();
        String key = q.toLowerCase();
        return nodes.values().stream()
                .filter(n -> (n.getDisplayName() != null && n.getDisplayName().toLowerCase().contains(key))
                        || (n.getSummary() != null && n.getSummary().toLowerCase().contains(key)))
                .map(n -> new WikiPageLite(n.getDisplayName(), n.getType(), n.getVersion()))
                .sorted(Comparator.comparing(WikiPageLite::displayName))
                .collect(Collectors.toList());
    }

    public int entityCount() {
        return nodes.size();
    }

    public int relationCount() {
        return nodes.values().stream().mapToInt(n -> n.getOut().size()).sum();
    }

    /** 树/搜索用的轻量实体视图。 */
    public record WikiPageLite(String displayName, String type, int version) {
    }

    /** 落盘包装结构。 */
    private static class Store {
        public String savedAt;
        public List<EntityNode> nodes;

        public Store() {
        }

        public Store(String savedAt, List<EntityNode> nodes) {
            this.savedAt = savedAt;
            this.nodes = nodes;
        }
    }
}
