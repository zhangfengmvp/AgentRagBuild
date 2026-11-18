package com.example.rag.rag;

import java.util.List;

/**
 * 混合检索的中间结果：稠密命中、稀疏命中、RRF 融合后排序。
 */
public record HybridResult(List<Hit> dense, List<Hit> sparse, List<Hit> fused) {
}
