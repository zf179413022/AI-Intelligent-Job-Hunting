package com.aijob.server.ai;

import com.aijob.server.entity.KnowledgeChunk;
import com.aijob.server.entity.KnowledgeDocument;
import com.aijob.server.entity.ai.InterviewAnswerAiResult;
import com.aijob.server.service.KnowledgeVectorStoreService;
import com.aijob.server.vo.KnowledgeSourceVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewTurnGraphTest {

    @Test
    void probesWhenTheRouteModelChoosesProbe() {
        InterviewAnswerAiResult result = graph(new ScriptedChatModel(), new UnusedStreamingModel(), new UnusedVectorStore())
                .run(input("我用 Redis 做缓存", 1));

        assertEquals("probe", result.getRouteAction());
        assertEquals("缓存说得太笼统", result.getEvaluation());
        assertEquals("缓存穿透怎么处理？", result.getNextQuestion());
        assertTrue(result.getShouldContinue());
    }

    @Test
    void switchesWhenTheRouteModelChoosesSwitch() {
        InterviewAnswerAiResult result = graph(new ScriptedChatModel(), new UnusedStreamingModel(), new UnusedVectorStore())
                .run(input("我觉得还行", 1));

        assertEquals("switch", result.getRouteAction());
        assertEquals("这题可以过", result.getEvaluation());
        assertTrue(result.getShouldContinue());
    }

    @Test
    void concludesAfterFiveUserRoundsWithoutAskingTheModel() {
        List<String> prompts = new ArrayList<>();
        InterviewAnswerAiResult result = graph(new ScriptedChatModel(prompts), new UnusedStreamingModel(), new UnusedVectorStore())
                .run(input("我用 Redis 做缓存", 5));

        assertEquals("conclude", result.getRouteAction());
        assertEquals("", result.getNextQuestion());
        assertFalse(result.getShouldContinue());
        assertTrue(prompts.stream().noneMatch(prompt -> prompt.contains("nextAction 只能是")));
    }

    @Test
    void fallsBackToSwitchWhenRouteJsonStaysInvalid() {
        List<String> prompts = new ArrayList<>();
        InterviewAnswerAiResult result = graph(new InvalidRouteModel(prompts, 2), new UnusedStreamingModel(), new UnusedVectorStore())
                .run(input("我觉得还行", 1));

        assertEquals("switch", result.getRouteAction());
        assertEquals("这题可以过", result.getEvaluation());
        assertEquals(2, prompts.stream().filter(prompt -> prompt.contains("nextAction 只能是")).count());
        assertTrue(prompts.stream().noneMatch(prompt -> prompt.contains("not-json")));
    }

    @Test
    void usesTheSecondRouteResponseWhenTheFirstJsonIsInvalid() {
        InterviewAnswerAiResult result = graph(new InvalidRouteModel(new ArrayList<>(), 1), new UnusedStreamingModel(), new UnusedVectorStore())
                .run(input("我用 Redis 做缓存", 1));

        assertEquals("probe", result.getRouteAction());
    }

    @Test
    void feedsTheToolResultBackToTheModelAndBindsTheServerUser() {
        AtomicLong seenUser = new AtomicLong(-1);
        List<String> toolTexts = new ArrayList<>();
        InterviewAnswerAiResult result = graph(
                new ToolThenRouteModel(toolTexts),
                new UnusedStreamingModel(),
                new RecordingStore(seenUser)
        ).run(input("我觉得还行", 1));

        assertEquals(9L, seenUser.get());
        assertEquals("probe", result.getRouteAction());
        assertEquals("SUCCESS", result.getToolStatus());
        assertEquals(1, toolTexts.size());
        assertTrue(toolTexts.getFirst().contains("\"status\":\"SUCCESS\""));
        assertTrue(toolTexts.getFirst().contains("过期键"));
        assertFalse(toolTexts.getFirst().contains("userId"));
    }

    @Test
    void streamsToolRouteAndDeltaWhenTheToolSucceeds() {
        List<String> events = new ArrayList<>();
        graph(new ToolThenRouteModel(new ArrayList<>()), new MarkerStreamingModel(), new RecordingStore(new AtomicLong()))
                .run(input("我觉得还行", 1), listener(events));

        assertEquals(List.of(
                "tool:searchKnowledgeBase:START",
                "tool:searchKnowledgeBase:SUCCESS",
                "route:probe",
                "delta"
        ), events);
        assertFalse(String.join(" ", events).contains("HashMap"));
        assertFalse(String.join(" ", events).contains("userId"));
    }

    @Test
    void streamsRouteWithoutToolWhenTheModelDoesNotCallIt() {
        List<String> events = new ArrayList<>();
        graph(new ScriptedChatModel(), new MarkerStreamingModel(), new UnusedVectorStore())
                .run(input("我觉得还行", 1), listener(events));

        assertEquals(List.of("route:switch", "delta"), events);
    }

    @Test
    void streamsToolFailureBeforeTheRoute() {
        List<String> events = new ArrayList<>();
        List<String> toolTexts = new ArrayList<>();
        graph(new ToolThenRouteModel(toolTexts), new MarkerStreamingModel(), new ExplodingStore())
                .run(input("我觉得还行", 1), listener(events));

        assertEquals(List.of(
                "tool:searchKnowledgeBase:START",
                "tool:searchKnowledgeBase:FAILED",
                "route:probe",
                "delta"
        ), events);
        assertTrue(toolTexts.getFirst().contains("无法对照知识库"));
        assertFalse(toolTexts.getFirst().contains("jdbc"));
        assertFalse(String.join(" ", events).contains("jdbc"));
    }

    private static InterviewTurnEvents listener(List<String> events) {
        return new InterviewTurnEvents() {
            @Override
            public void onTool(String name, String status) {
                events.add("tool:" + name + ":" + status);
            }

            @Override
            public void onRoute(String action) {
                events.add("route:" + action);
            }

            @Override
            public void onDelta(String content) {
                if (events.isEmpty() || !"delta".equals(events.getLast())) {
                    events.add("delta");
                }
            }
        };
    }

    private static InterviewTurnGraph graph(ChatModel chat, StreamingChatModel streaming, KnowledgeVectorStoreService store) {
        return new InterviewTurnGraph(
                chat,
                streaming,
                new DeepSeekJsonChat(chat, new ObjectMapper()),
                store,
                null,
                0.2
        );
    }

    private static InterviewTurnInput input(String answer, int rounds) {
        return new InterviewTurnInput("Java 后端", "熟悉 Spring", "（暂无历史对话）", "介绍一个项目", answer, rounds, 9L, 11L);
    }

    private static String textOf(ChatRequest request) {
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : request.messages()) {
            if (message instanceof SystemMessage systemMessage) {
                builder.append(systemMessage.text());
            } else if (message instanceof UserMessage userMessage) {
                builder.append(userMessage.singleText());
            } else if (message instanceof ToolExecutionResultMessage toolMessage) {
                builder.append(toolMessage.text());
            }
        }
        return builder.toString();
    }

    private static ChatResponse json(String body) {
        return ChatResponse.builder().aiMessage(AiMessage.from(body)).build();
    }

    private static final class ScriptedChatModel implements ChatModel {
        private final List<String> prompts;

        private ScriptedChatModel() {
            this(new ArrayList<>());
        }

        private ScriptedChatModel(List<String> prompts) {
            this.prompts = prompts;
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            String text = textOf(request);
            prompts.add(text);
            if (text.contains("nextAction 只能是") && text.toLowerCase().contains("redis")) {
                return json("{\"score\":0.4,\"nextAction\":\"probe\",\"reason\":\"缓存细节不够\"}");
            }
            if (text.contains("nextAction 只能是")) {
                return json("{\"score\":0.8,\"nextAction\":\"switch\",\"reason\":\"可以换题\"}");
            }
            if (text.contains("生成追问")) {
                return json("{\"evaluation\":\"缓存说得太笼统\",\"nextQuestion\":\"缓存穿透怎么处理？\",\"shouldContinue\":true}");
            }
            if (text.contains("生成下一题")) {
                return json("{\"evaluation\":\"这题可以过\",\"nextQuestion\":\"事务隔离级别有哪些？\",\"shouldContinue\":true}");
            }
            if (text.contains("收束")) {
                return json("{\"evaluation\":\"本轮可以结束\",\"nextQuestion\":\"\",\"shouldContinue\":false}");
            }
            throw new IllegalStateException("脚本没有覆盖这次提示");
        }
    }

    private static final class InvalidRouteModel implements ChatModel {
        private final List<String> prompts;
        private final int invalidAttempts;
        private int routeCalls;

        private InvalidRouteModel(List<String> prompts, int invalidAttempts) {
            this.prompts = prompts;
            this.invalidAttempts = invalidAttempts;
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            String text = textOf(request);
            prompts.add(text);
            if (text.contains("nextAction 只能是")) {
                routeCalls++;
                if (routeCalls <= invalidAttempts) {
                    return json("not-json");
                }
                if (text.toLowerCase().contains("redis")) {
                    return json("{\"nextAction\":\"probe\",\"reason\":\"缓存细节不够\"}");
                }
                return json("{\"nextAction\":\"switch\",\"reason\":\"可以换题\"}");
            }
            if (text.contains("生成追问")) {
                return json("{\"evaluation\":\"缓存说得太笼统\",\"nextQuestion\":\"缓存穿透怎么处理？\",\"shouldContinue\":true}");
            }
            if (text.contains("生成下一题")) {
                return json("{\"evaluation\":\"这题可以过\",\"nextQuestion\":\"事务隔离级别有哪些？\",\"shouldContinue\":true}");
            }
            throw new IllegalStateException("脚本没有覆盖这次提示");
        }
    }

    private static final class ToolThenRouteModel implements ChatModel {
        private final List<String> toolTexts;

        private ToolThenRouteModel(List<String> toolTexts) {
            this.toolTexts = toolTexts;
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            for (ChatMessage message : request.messages()) {
                if (message instanceof ToolExecutionResultMessage toolMessage) {
                    toolTexts.add(toolMessage.text());
                    return json("{\"nextAction\":\"probe\",\"reason\":\"对照资料后还要追问\"}");
                }
            }
            if (textOf(request).contains("nextAction 只能是")) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                                .id("call-1")
                                .name("searchKnowledgeBase")
                                .arguments("{\"query\":\"HashMap\"}")
                                .build()))
                        .build();
            }
            if (textOf(request).contains("生成追问")) {
                return json("{\"evaluation\":\"缓存说得太笼统\",\"nextQuestion\":\"缓存穿透怎么处理？\",\"shouldContinue\":true}");
            }
            throw new IllegalStateException("脚本没有覆盖这次提示");
        }
    }

    private static final class MarkerStreamingModel implements StreamingChatModel {
        @Override
        public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
            String text = """
                    【点评】
                    缓存说得太笼统
                    【追问】
                    缓存穿透怎么处理？
                    【继续】
                    true
                    """;
            handler.onPartialResponse(text);
            handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from(text)).build());
        }
    }

    private static final class UnusedStreamingModel implements StreamingChatModel {
        @Override
        public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
            throw new UnsupportedOperationException("同步用例不应进入流式节点");
        }
    }

    static class UnusedVectorStore implements KnowledgeVectorStoreService {
        @Override
        public void deleteByDocument(Long documentId, Long userId) {
        }

        @Override
        public List<String> upsertChunks(KnowledgeDocument document, List<KnowledgeChunk> chunks, List<Embedding> embeddings) {
            return List.of();
        }

        @Override
        public long countByDocument(Long documentId, Long userId) {
            return 0;
        }

        @Override
        public List<KnowledgeSourceVO> searchTopK(String question, Long userId, Long documentId, int topK, double minScore) {
            return List.of();
        }
    }

    private static final class RecordingStore extends UnusedVectorStore {
        private final AtomicLong seenUser;

        private RecordingStore(AtomicLong seenUser) {
            this.seenUser = seenUser;
        }

        @Override
        public List<KnowledgeSourceVO> searchTopK(String question, Long userId, Long documentId, int topK, double minScore) {
            seenUser.set(userId == null ? -1 : userId);
            return List.of(new KnowledgeSourceVO(1L, "Redis 笔记", 2L, "过期键会在惰性删除或定期删除时清掉", 0.86));
        }
    }

    private static final class ExplodingStore extends UnusedVectorStore {
        @Override
        public List<KnowledgeSourceVO> searchTopK(String question, Long userId, Long documentId, int topK, double minScore) {
            throw new RuntimeException("jdbc:mysql://root:password@internal-db/job");
        }
    }
}
