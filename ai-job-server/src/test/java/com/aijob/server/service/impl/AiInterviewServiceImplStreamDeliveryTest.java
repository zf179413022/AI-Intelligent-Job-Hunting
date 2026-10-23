package com.aijob.server.service.impl;

import com.aijob.server.ai.DeepSeekJsonChat;
import com.aijob.server.ai.InterviewTurnGraph;
import com.aijob.server.entity.KnowledgeChunk;
import com.aijob.server.entity.KnowledgeDocument;
import com.aijob.server.entity.ai.InterviewAnswerAiResult;
import com.aijob.server.service.InterviewAnswerStreamHandler;
import com.aijob.server.service.KnowledgeVectorStoreService;
import com.aijob.server.vo.KnowledgeSourceVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiInterviewServiceImplStreamDeliveryTest {

    @Test
    void stillPersistsCallbackWhenToolNotificationThrows() {
        assertDoneArrives("tool");
    }

    @Test
    void stillPersistsCallbackWhenRouteNotificationThrows() {
        assertDoneArrives("route");
    }

    @Test
    void stillPersistsCallbackWhenDeltaNotificationThrows() {
        assertDoneArrives("delta");
    }

    private static void assertDoneArrives(String failingEvent) {
        AiInterviewServiceImpl service = new AiInterviewServiceImpl(
                new DeepSeekJsonChat(new RouteModel(), new ObjectMapper()),
                new InterviewTurnGraph(
                        new RouteModel(),
                        new MarkerStreamingModel(),
                        new DeepSeekJsonChat(new RouteModel(), new ObjectMapper()),
                        new QuietStore(),
                        null,
                        0.2
                )
        );
        AtomicBoolean done = new AtomicBoolean(false);
        AtomicReference<String> error = new AtomicReference<>();
        service.answerStream(
                "熟悉 Java",
                "Java 后端",
                List.of(),
                "介绍一个项目",
                "我觉得还行",
                9L,
                11L,
                new InterviewAnswerStreamHandler() {
                    @Override
                    public void onTool(String name, String status) {
                        if ("tool".equals(failingEvent)) {
                            throw new RuntimeException(new IOException("broken pipe"));
                        }
                    }

                    @Override
                    public void onRoute(String action) {
                        if ("route".equals(failingEvent)) {
                            throw new RuntimeException(new IOException("broken pipe"));
                        }
                    }

                    @Override
                    public void onDelta(String content) {
                        if ("delta".equals(failingEvent)) {
                            throw new RuntimeException(new IOException("broken pipe"));
                        }
                    }

                    @Override
                    public void onDone(InterviewAnswerAiResult result) {
                        done.set(result != null && "probe".equals(result.getRouteAction()));
                    }

                    @Override
                    public void onError(String message) {
                        error.set(message);
                    }
                }
        );

        assertTrue(done.get(), "失败事件=" + failingEvent + "，error=" + error.get());
        assertEquals(null, error.get());
    }

    private static final class RouteModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            for (ChatMessage message : request.messages()) {
                if (message instanceof ToolExecutionResultMessage) {
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.from("{\"nextAction\":\"probe\",\"reason\":\"继续追问\"}"))
                            .build();
                }
            }
            String text = request.messages().toString();
            if (text.contains("nextAction 只能是")) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                                .id("call-1")
                                .name("searchKnowledgeBase")
                                .arguments("{\"query\":\"HashMap\"}")
                                .build()))
                        .build();
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(
                            "{\"evaluation\":\"不错\",\"nextQuestion\":\"下一题\",\"shouldContinue\":true}"
                    ))
                    .build();
        }
    }

    private static final class MarkerStreamingModel implements StreamingChatModel {
        @Override
        public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
            String text = """
                    【点评】
                    不错
                    【追问】
                    下一题
                    【继续】
                    true
                    """;
            handler.onPartialResponse(text);
            handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from(text)).build());
        }
    }

    private static final class QuietStore implements KnowledgeVectorStoreService {
        @Override
        public void deleteByDocument(Long documentId, Long userId) {
        }

        @Override
        public List<String> upsertChunks(
                KnowledgeDocument document,
                List<KnowledgeChunk> chunks,
                List<Embedding> embeddings) {
            return List.of();
        }

        @Override
        public long countByDocument(Long documentId, Long userId) {
            return 0;
        }

        @Override
        public List<KnowledgeSourceVO> searchTopK(
                String question,
                Long userId,
                Long documentId,
                int topK,
                double minScore) {
            return List.of(new KnowledgeSourceVO(1L, "笔记", 2L, "扩容", 0.8));
        }
    }
}
