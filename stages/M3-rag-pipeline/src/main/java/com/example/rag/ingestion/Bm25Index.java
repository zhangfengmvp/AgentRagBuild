package com.example.rag.ingestion;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地轻量 BM25 倒排索引（稀疏检索），零外部依赖，常驻内存。
 *
 * 设计取舍（学习版）：
 * - 分词：英文/数字按词（小写）；中文按字 + 相邻二字 bigram。不引入分词器依赖，足够演示稀疏召回。
 * - 入库时由 IngestionService 逐切片 add；查询时按 BM25 公式打分召回 top-K。
 * - 与稠密检索（Qdrant 向量）同源：同一批切片既进向量库也进本索引，保证可融合。
 */
@Service
public class Bm25Index {

    private final Map<String, DocMeta> docs = new HashMap<>();
    private final Map<String, List<Posting>> inverted = new HashMap<>();
    private final Map<String, List<String>> docTerms = new HashMap<>();
    private final AtomicInteger totalDocs = new AtomicInteger(0);
    private final AtomicLong totalLen = new AtomicLong(0);

    private static final Pattern WORD = Pattern.compile("[a-zA-Z0-9]+");
    private static final double K1 = 1.5;
    private static final double B = 0.75;

    public record DocMeta(String docId, String text, String source, String heading) {
    }

    private record Posting(String docId, int tf) {
    }

    public record ScoredDoc(String docId, String source, String heading, String text, double score) {
    }

    /** 重建索引：先清空，再逐切片写入。保证每次入库后稠密/稀疏同源一致。 */
    public synchronized void clear() {
        docs.clear();
        inverted.clear();
        docTerms.clear();
        totalDocs.set(0);
        totalLen.set(0);
    }

    public synchronized void add(String docId, String text, String source, String heading) {
        if (docTerms.containsKey(docId)) {
            remove(docId);
        }
        List<String> tokens = tokenize(text);
        Map<String, Integer> tf = new HashMap<>();
        for (String t : tokens) {
            tf.merge(t, 1, Integer::sum);
        }
        docs.put(docId, new DocMeta(docId, text, source, heading));
        docTerms.put(docId, tokens);
        totalDocs.incrementAndGet();
        totalLen.addAndGet(tokens.size());
        for (Map.Entry<String, Integer> e : tf.entrySet()) {
            inverted.computeIfAbsent(e.getKey(), k -> new ArrayList<>())
                    .add(new Posting(docId, e.getValue()));
        }
    }

    private synchronized void remove(String docId) {
        DocMeta meta = docs.remove(docId);
        if (meta == null) {
            return;
        }
        List<String> terms = docTerms.remove(docId);
        if (terms != null) {
            for (String t : terms) {
                List<Posting> postings = inverted.get(t);
                if (postings != null) {
                    postings.removeIf(p -> p.docId().equals(docId));
                    if (postings.isEmpty()) {
                        inverted.remove(t);
                    }
                }
            }
        }
        totalDocs.decrementAndGet();
    }

    /** BM25 检索：返回 topK 个片段及得分（不同语料规模下均可工作）。 */
    public List<ScoredDoc> search(String query, int topK) {
        List<String> qterms = tokenize(query);
        if (qterms.isEmpty()) {
            return List.of();
        }
        int n = totalDocs.get();
        double avgdl = n == 0 ? 1 : (double) totalLen.get() / n;
        Map<String, Double> scores = new HashMap<>();
        // 同一查询词只算一次 IDF，但多次出现仍按 TF 累加
        for (String t : new HashSet<>(qterms)) {
            List<Posting> postings = inverted.get(t);
            if (postings == null) {
                continue;
            }
            double idf = Math.log((n - postings.size() + 0.5) / (postings.size() + 0.5) + 1.0);
            for (Posting p : postings) {
                DocMeta dm = docs.get(p.docId());
                int dl = docTerms.get(p.docId()).size();
                double denom = p.tf() + K1 * (1 - B + B * (dl / avgdl));
                double s = idf * (p.tf() * (K1 + 1)) / denom;
                scores.merge(p.docId(), s, Double::sum);
            }
        }
        List<Map.Entry<String, Double>> ranked = new ArrayList<>(scores.entrySet());
        ranked.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<ScoredDoc> out = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, ranked.size()); i++) {
            DocMeta dm = docs.get(ranked.get(i).getKey());
            out.add(new ScoredDoc(dm.docId(), dm.source(), dm.heading(), dm.text(), ranked.get(i).getValue()));
        }
        return out;
    }

    public int size() {
        return totalDocs.get();
    }

    /** 分词：英文/数字词 + 中文单字 + 中文相邻 bigram。 */
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher m = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            tokens.add(m.group());
        }
        StringBuilder run = new StringBuilder();
        int cp;
        for (int i = 0; i < text.length(); i += Character.charCount(cp)) {
            cp = text.codePointAt(i);
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) {
                run.appendCodePoint(cp);
            } else {
                emitCjk(run, tokens);
                run.setLength(0);
            }
        }
        emitCjk(run, tokens);
        return tokens;
    }

    private void emitCjk(StringBuilder run, List<String> tokens) {
        String s = run.toString();
        for (int i = 0; i < s.length(); i++) {
            tokens.add(String.valueOf(s.charAt(i)));
        }
        for (int i = 0; i + 1 < s.length(); i++) {
            tokens.add(s.substring(i, i + 2));
        }
    }
}
