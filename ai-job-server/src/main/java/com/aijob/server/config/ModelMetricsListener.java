package com.aijob.server.config;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 记录 DeepSeek 调用的 token、耗时和错误类型。
 * 不记录 Prompt、简历正文和 API Key。耗时用监听器自己的起点，ChatResponse 没有 latency 字段。
 * 本地 ONNX Embedding 不经过 ChatModel，也没有 token 账单，不记在这里。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelMetricsListener implements ChatModelListener {

    private static final Object START_NANOS = new Object();
    private static final String UNKNOWN_MODEL = "unknown";

    private final MeterRegistry meterRegistry;

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        requestContext.attributes().put(START_NANOS, System.nanoTime());
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        ChatResponse response = responseContext.chatResponse();
        String model = modelName(responseContext.chatRequest(), response);
        recordDuration(responseContext.attributes().get(START_NANOS), model, "success");

        if (response == null || response.metadata() == null || response.metadata().tokenUsage() == null) {
            return;
        }
        TokenUsage usage = response.metadata().tokenUsage();
        increment("ai.tokens.prompt", model, usage.inputTokenCount());
        increment("ai.tokens.completion", model, usage.outputTokenCount());
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        String model = modelName(errorContext.chatRequest(), null);
        recordDuration(errorContext.attributes().get(START_NANOS), model, "error");
        meterRegistry.counter(
                "ai.call.errors",
                "model", model,
                "error", errorContext.error().getClass().getSimpleName()
        ).increment();
        log.warn("DeepSeek 调用失败, model={}, error={}",
                model, errorContext.error().getClass().getSimpleName());
    }

    private void recordDuration(Object startNanos, String model, String status) {
        if (!(startNanos instanceof Long start)) {
            return;
        }
        meterRegistry.timer("ai.call.duration", "model", model, "status", status)
                .record(Duration.ofNanos(Math.max(0, System.nanoTime() - start)));
    }

    private void increment(String name, String model, Integer count) {
        if (count == null || count <= 0) {
            return;
        }
        meterRegistry.counter(name, "model", model).increment(count);
    }

    private static String modelName(ChatRequest request, ChatResponse response) {
        if (response != null && response.metadata() != null && response.metadata().modelName() != null
                && !response.metadata().modelName().isBlank()) {
            return response.metadata().modelName();
        }
        if (request != null && request.parameters() != null && request.parameters().modelName() != null
                && !request.parameters().modelName().isBlank()) {
            return request.parameters().modelName();
        }
        return UNKNOWN_MODEL;
    }
}
