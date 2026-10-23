package com.aijob.server.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;

/**
 * 单次 DeepSeek JSON 调用。响应格式只加在这一次请求上，避免把全局 ChatModel 改成 JSON，影响知识库纯文本回答。
 */
@Component
@RequiredArgsConstructor
public class DeepSeekJsonChat {

    private static final ResponseFormat JSON_OBJECT = ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .build();

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public <T> T complete(String system, String user, double temperature, Class<T> type) {
        return ModelJsonParser.read(objectMapper, requestText(system, user, temperature), type);
    }

    /**
     * 结构化结果不合法时用同一提示再调一次。仍然不合法就抛错，调用方不得把结果入库。
     * 传输失败不在这里重试，避免把超时放大成两次。
     */
    public <T> T completeValid(
            String system,
            String user,
            double temperature,
            Class<T> type,
            Predicate<JsonNode> validator,
            String invalidMessage) {
        JsonNode first = readTree(system, user, temperature);
        if (first != null && validator.test(first)) {
            return convert(first, type, invalidMessage);
        }
        JsonNode second = readTree(system, user, temperature);
        if (second != null && validator.test(second)) {
            return convert(second, type, invalidMessage);
        }
        throw new RuntimeException(invalidMessage);
    }

    private JsonNode readTree(String system, String user, double temperature) {
        try {
            String raw = requestText(system, user, temperature);
            return ModelJsonParser.readTree(objectMapper, raw);
        } catch (RuntimeException e) {
            if ("模型未返回内容".equals(e.getMessage())) {
                return null;
            }
            if (isTransport(e)) {
                throw e;
            }
            return null;
        }
    }

    private <T> T convert(JsonNode node, Class<T> type, String invalidMessage) {
        try {
            return objectMapper.treeToValue(node, type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException(invalidMessage, e);
        }
    }

    private String requestText(String system, String user, double temperature) {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(SystemMessage.from(system), UserMessage.from(user)))
                .parameters(OpenAiChatRequestParameters.builder()
                        .temperature(temperature)
                        .responseFormat(JSON_OBJECT)
                        .build())
                .build();
        try {
            ChatResponse response = chatModel.chat(request);
            if (response == null || response.aiMessage() == null || response.aiMessage().text() == null) {
                throw new RuntimeException("模型未返回内容");
            }
            return response.aiMessage().text();
        } catch (RuntimeException e) {
            if ("模型未返回内容".equals(e.getMessage()) || isTransport(e)) {
                throw e;
            }
            throw new RuntimeException("调用 DeepSeek 失败", e);
        } catch (Exception e) {
            throw new RuntimeException("调用 DeepSeek 失败", e);
        }
    }

    private static boolean isTransport(RuntimeException e) {
        return e.getMessage() != null && e.getMessage().contains("调用 DeepSeek 失败");
    }
}
