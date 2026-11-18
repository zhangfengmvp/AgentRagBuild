package com.example.rag.ingestion;

import com.example.rag.config.RagProperties;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

/**
 * 入库管道（RAG 离线阶段的编排者，由 /api/ingest 触发）：
 *   1. 遍历 corpus/ 下的所有 .md 文件
 *   2. MarkdownSectionParser 按标题把每篇文档切成"节"（heading + 正文）
 *   3. TextChunker 把长节按段落边界切片（带 overlap），便于向量检索命中
 *   4. DashScope EmbeddingModel 把每个切片文本转成向量（text-embedding-v3）
 *   5. 通过 VectorStore 写入 Qdrant（collection = application.yml 中的 java_knowledge）
 *
 * 幂等策略：切片 ID 由 (相对路径 + 标题 + 序号) 用 nameUUIDFromBytes 确定性生成，
 * 重复入库时同 ID 覆盖更新，不会产生重复数据（可反复点击「入库语料」）。
 *
 * 注意：本类的 Document 是 org.springframework.ai.document.Document（Spring AI 文档模型），
 * Qdrant 客户端走 gRPC（application.yml 的 6334 端口，非 REST 的 6333）。
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final RagProperties props;
    private final MarkdownSectionParser parser;
    private final TextChunker chunker;
    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final QdrantClient qdrantClient;
    private final String collection;

    public IngestionService(RagProperties props,
                            MarkdownSectionParser parser,
                            TextChunker chunker,
                            VectorStore vectorStore,
                            EmbeddingModel embeddingModel,
                            QdrantClient qdrantClient,
                            @Value("${spring.ai.vectorstore.qdrant.collection-name:java_knowledge}") String collection) {
        this.props = props;
        this.parser = parser;
        this.chunker = chunker;
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.qdrantClient = qdrantClient;
        this.collection = collection;
    }

    public record IngestResult(int files, int chunks) {
    }

    public IngestResult ingest() throws IOException, ExecutionException, InterruptedException {
        Path corpus = Path.of(props.corpusDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(corpus)) {
            throw new IllegalStateException("语料目录不存在: " + corpus + "（请在 stage 根目录运行）");
        }

        // 步骤0：确保 Qdrant 中 collection 存在（首次按需创建，维度取自当前 embedding 模型）
        ensureCollection();

        int files = 0;
        int chunks = 0;
        List<Document> batch = new ArrayList<>();

        // 步骤1：扫描语料目录（corpus/）下所有 .md（排除 README.md）
        try (Stream<Path> paths = Files.walk(corpus)) {
            List<Path> mdFiles = paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md"))
                    .filter(p -> !p.getFileName().toString().equals("README.md"))
                    .toList();

            for (Path p : mdFiles) {
                files++;
                String rel = corpus.relativize(p).toString();   // 相对路径作为来源标识
                String md = Files.readString(p, StandardCharsets.UTF_8);

                // 步骤2：按标题切成"节"
                for (MarkdownSectionParser.Section section : parser.parse(md)) {
                    if (section.content().isBlank()) {
                        continue;
                    }
                    // 步骤3：长节按段落边界切片（带 overlap）
                    for (TextChunker.Chunk chunk : chunker.chunk(section.content())) {
                        // 确定性 ID：相同来源+标题+序号 → 同 ID，保证幂等（重入库覆盖而非重复）
                        String id = UUID.nameUUIDFromBytes(
                                (rel + "#" + section.heading() + "#" + chunk.index())
                                        .getBytes(StandardCharsets.UTF_8)).toString();
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("source", rel);
                        metadata.put("heading", section.heading());
                        metadata.put("chunkIndex", chunk.index());
                        // Spring AI 的 Document：text=切片正文，metadata=来源信息（供检索后引用溯源）
                        batch.add(Document.builder()
                                .id(id)
                                .text(chunk.text())
                                .metadata(metadata)
                                .build());
                        chunks++;
                    }
                }
            }
        }

        // 步骤4+5：VectorStore.add 内部自动调用 EmbeddingModel 向量化并写入 Qdrant
        if (!batch.isEmpty()) {
            vectorStore.add(batch);
        }
        log.info("入库完成：{} 个文件，{} 个切片，collection={}", files, chunks, collection);
        return new IngestResult(files, chunks);
    }

    /** 若 collection 不存在，则按当前 embedding 模型的维度创建（余弦相似度）。 */
    private void ensureCollection() throws ExecutionException, InterruptedException {
        boolean exists = qdrantClient.listCollectionsAsync().get()
                .stream().anyMatch(c -> c.equals(collection));
        if (exists) {
            return;
        }
        int dim = embeddingModel.dimensions();
        qdrantClient.createCollectionAsync(
                collection,
                VectorParams.newBuilder().setSize((long) dim).setDistance(Distance.Cosine).build()).get();
        log.info("已创建 Qdrant collection: {} (dim={})", collection, dim);
    }
}
