package com.example.rag.ingestion;

import com.example.rag.config.RagProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 长文本切片：优先按段落（空行）边界断开，带 overlap，避免语义被拦腰截断。
 */
@Component
public class TextChunker {

    private final RagProperties props;

    public TextChunker(RagProperties props) {
        this.props = props;
    }

    public record Chunk(String text, int index) {
    }

    public List<Chunk> chunk(String text) {
        RagProperties.Chunk cfg = props.chunk();
        List<Chunk> out = new ArrayList<>();
        String t = text.strip();
        if (t.isEmpty()) {
            return out;
        }
        if (t.length() <= cfg.maxChars()) {
            out.add(new Chunk(t, 0));
            return out;
        }

        int start = 0;
        int index = 0;
        while (start < t.length()) {
            int end = Math.min(start + cfg.maxChars(), t.length());
            if (end < t.length()) {
                int cut = t.lastIndexOf("\n", end);
                if (cut > start + cfg.maxChars() / 2) {
                    end = cut;
                }
            }
            out.add(new Chunk(t.substring(start, end).strip(), index++));
            if (end >= t.length()) {
                break;
            }
            // 回退 overlap 个字符作为下一片起点，相邻切片重叠，避免语义被硬切断
            start = Math.max(end - cfg.overlap(), start + 1);
        }
        return out;
    }
}
