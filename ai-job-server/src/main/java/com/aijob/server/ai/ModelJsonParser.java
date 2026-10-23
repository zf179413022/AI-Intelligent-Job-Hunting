package com.aijob.server.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Locale;

/**
 * 把模型文本收成 JSON。
 * DeepSeek 在要求 json_object 时仍可能包一层 markdown 围栏，这里只解析同一次响应，不再发起第二次调用。
 */
public final class ModelJsonParser {

    private ModelJsonParser() {
    }

    public static String stripMarkdownFence(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (!text.startsWith("```")) {
            return text;
        }

        int firstNewline = text.indexOf('\n');
        if (firstNewline < 0) {
            String inner = text.substring(3).trim();
            if (inner.toLowerCase(Locale.ROOT).startsWith("json")) {
                inner = inner.substring(4).trim();
            }
            if (inner.endsWith("```")) {
                inner = inner.substring(0, inner.length() - 3).trim();
            }
            return inner;
        }

        String body = text.substring(firstNewline + 1);
        int close = body.lastIndexOf("```");
        if (close >= 0) {
            body = body.substring(0, close);
        }
        return body.trim();
    }

    public static <T> T read(ObjectMapper objectMapper, String raw, Class<T> type) {
        String json = stripMarkdownFence(raw);
        try {
            return objectMapper.readerFor(type)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("模型返回的 JSON 无法解析", e);
        }
    }

    public static JsonNode readTree(ObjectMapper objectMapper, String raw) {
        String json = stripMarkdownFence(raw);
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || node.isMissingNode()) {
                throw new RuntimeException("模型返回的 JSON 无法解析");
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("模型返回的 JSON 无法解析", e);
        }
    }

    /**
     * 只接受 JSON 整数。小数会被 Jackson 收成整数，这里在转换前拒绝，避免把 150.5 收成 150。
     */
    public static boolean integralScore(JsonNode node, String field) {
        if (node == null || field == null) {
            return false;
        }
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            return false;
        }
        int score = value.intValue();
        return score >= 0 && score <= 100;
    }

    public static boolean textPresent(JsonNode node, String field) {
        if (node == null || field == null) {
            return false;
        }
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank();
    }
}
