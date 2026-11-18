package com.example.rag.web;

import com.example.rag.agent.AgentEventSink;
import com.example.rag.agent.AgentStep;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * 把 {@link AgentEventSink} 的事件编码为 SSE 帧：
 * <ul>
 *   <li>step 事件 → name="step"，data=AgentStep JSON；</li>
 *   <li>token 事件 → name="token"，data=文本片段；</li>
 *   <li>结束 → name="done" 后 complete()；</li>
 *   <li>异常 → name="error" 后 completeWithError()。</li>
 * </ul>
 * 客户端断开时 send 抛 IOException，这里转成 {@link SseClosedException} 交由 runStream 的 try/catch 静默收尾。
 */
public class SseEmitterSink implements AgentEventSink {

    private final SseEmitter emitter;

    public SseEmitterSink(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void emitStep(AgentStep step) {
        try {
            emitter.send(SseEmitter.event().name("step").data(step));
        } catch (IOException e) {
            throw new SseClosedException(e);
        }
    }

    @Override
    public void emitToken(String token) {
        try {
            emitter.send(SseEmitter.event().name("token").data(token));
        } catch (IOException e) {
            throw new SseClosedException(e);
        }
    }

    @Override
    public void complete() {
        try {
            emitter.send(SseEmitter.event().name("done").data(""));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    @Override
    public void error(Throwable t) {
        try {
            emitter.send(SseEmitter.event().name("error").data(t.getMessage() == null ? "" : t.getMessage()));
        } catch (IOException ignored) {
            // 客户端已断开，忽略
        }
        emitter.completeWithError(t);
    }

    /** 标记 SSE 连接已关闭，供 runStream 的 catch 静默处理。 */
    static final class SseClosedException extends RuntimeException {
        SseClosedException(IOException cause) {
            super(cause);
        }
    }
}
