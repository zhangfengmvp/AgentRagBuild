package com.example.rag.rag;

import com.example.rag.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管道编排：把 M3 的四个阶段串成一条链，产出可供前端可视化的 PipelineResult。
 *   原始问题 → (Query 改写) → 混合检索(RRF 融合) → Rerank → 生成答案 + 引用。
 */
@Service
public class PipelineService {

    private static final Logger log = LoggerFactory.getLogger(PipelineService.class);

    private final QueryRewriter rewriter;
    private final HybridSearcher hybridSearcher;
    private final Reranker reranker;
    private final AnswerService answerService;
    private final RagProperties props;

    public PipelineService(QueryRewriter rewriter,
                          HybridSearcher hybridSearcher,
                          Reranker reranker,
                          AnswerService answerService,
                          RagProperties props) {
        this.rewriter = rewriter;
        this.hybridSearcher = hybridSearcher;
        this.reranker = reranker;
        this.answerService = answerService;
        this.props = props;
    }

    public PipelineResult run(String question) {
        // 1) Query 改写（可关闭，降级为原问题）
        String rewritten = props.queryRewriteEnabled() ? rewriter.rewrite(question) : question;
        String retrievalQuery = (rewritten == null || rewritten.isBlank()) ? question : rewritten;

        // 2) 混合检索 + RRF 融合
        HybridResult hr = hybridSearcher.search(retrievalQuery);

        // 3) Rerank 只对融合后的前 N 条做（控制成本）
        List<Hit> toRerank = hr.fused().stream().limit(props.rerankTopN()).toList();
        List<Hit> reranked = reranker.rerank(retrievalQuery, toRerank);

        // 4) 依据重排后的片段生成答案
        AnswerService.Answer ans = answerService.generate(question, reranked);

        return new PipelineResult(question, rewritten, hr.dense(), hr.sparse(), hr.fused(), reranked,
                ans.answer(), ans.citations());
    }
}
