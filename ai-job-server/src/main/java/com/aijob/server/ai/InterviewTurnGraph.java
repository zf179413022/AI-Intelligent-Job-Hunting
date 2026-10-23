package com.aijob.server.ai;

import com.aijob.server.entity.ai.InterviewAnswerAiResult;
import com.aijob.server.service.KnowledgeVectorStoreService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.service.AiServices;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * 一轮回答内的编排：评分节点写出 nextAction，条件边路由到追问、换题或收束。
 * 节点里的模型调用走 LangChain4j，不使用 Spring AI ChatClient。
 * 等人回答仍由 HTTP 和 Redis Session 负责，图不在进程里阻塞等待下一次输入。
 */
@Slf4j
@Component
public class InterviewTurnGraph {

    static final String PROBE = "probe";
    static final String SWITCH = "switch";
    static final String CONCLUDE = "conclude";
    static final int MAX_USER_ROUNDS = 5;

    private static final double TEMPERATURE = 0.3;

    private static final ObjectMapper ROUTE_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String ROUTE_SYSTEM = """
            你是技术面试官。根据候选人这一轮回答决定下一步，只返回 JSON，不要 markdown。
            字段：
            {"score":0.0,"nextAction":"probe","reason":"简要依据"}
            nextAction 只能是 probe、switch、conclude。
            规则：
            - 回答碰到了具体技术点但没讲清，用 probe
            - 回答已经充分，或这题不值得再挖，用 switch
            - 回答无法继续，或该收束了，用 conclude
            需要对照知识库时调用 searchKnowledgeBase，只传 query，不要传 userId。
            工具返回 JSON。只有 status=SUCCESS 时，sources 才能作为依据。
            status=FAILED 或 sources 为空时，必须认为当前无法获得可靠的知识库依据，不得声称根据知识库得出结论。
            检索失败就按回答本身判断，不要编造资料。
            决定 nextAction 时不要输出点评正文。
            """;

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;
    private final DeepSeekJsonChat jsonChat;
    private final KnowledgeVectorStoreService vectorStore;
    private final MeterRegistry meterRegistry;
    private final double minScore;
    private final CompiledGraph graph;
    private final ConcurrentHashMap<String, InterviewTurnEvents> events = new ConcurrentHashMap<>();

    public InterviewTurnGraph(
            ChatModel chatModel,
            StreamingChatModel streamingChatModel,
            DeepSeekJsonChat jsonChat,
            KnowledgeVectorStoreService vectorStore,
            MeterRegistry meterRegistry,
            @Value("${knowledge.rag.min-score:0.2}") double minScore) {
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
        this.jsonChat = jsonChat;
        this.vectorStore = vectorStore;
        this.meterRegistry = meterRegistry;
        this.minScore = minScore;
        this.graph = compile();
    }

    public InterviewAnswerAiResult run(InterviewTurnInput input) {
        return run(input, null);
    }

    public InterviewAnswerAiResult run(InterviewTurnInput input, InterviewTurnEvents listener) {
        String turnId = UUID.randomUUID().toString();
        if (listener != null) {
            events.put(turnId, listener);
        }
        try {
            OverAllState state = graph.invoke(initialState(input, turnId))
                    .orElseThrow(() -> new RuntimeException("面试编排没有返回结果"));
            InterviewAnswerAiResult result = new InterviewAnswerAiResult();
            result.setEvaluation(state.value("evaluation", String.class).orElse(""));
            result.setNextQuestion(state.value("nextQuestion", String.class).orElse(""));
            result.setShouldContinue(state.value("shouldContinue", Boolean.class).orElse(Boolean.FALSE));
            result.setRouteAction(state.value("nextAction", String.class).orElse(SWITCH));
            result.setToolStatus(state.value("toolStatus", String.class).orElse("NONE"));
            return InterviewAnswerMarkers.applyPolicy(result, input.userRounds());
        } catch (RuntimeException e) {
            if (causedByMissingQuestion(e)) {
                throw new RuntimeException("AI未返回下一题", e);
            }
            log.warn("面试编排失败, error={}", rootMessage(e));
            throw new RuntimeException("调用 DeepSeek 失败", e);
        } finally {
            events.remove(turnId);
        }
    }

    static String normalizeAction(String raw) {
        if (raw == null) {
            return SWITCH;
        }
        String value = raw.strip().toLowerCase(Locale.ROOT);
        return switch (value) {
            case PROBE, SWITCH, CONCLUDE -> value;
            default -> SWITCH;
        };
    }

    private CompiledGraph compile() {
        KeyStrategyFactory strategies = () -> {
            Map<String, com.alibaba.cloud.ai.graph.KeyStrategy> keys = new HashMap<>();
            for (String key : new String[]{
                    "position", "resume", "history", "question", "answer",
                    "userRounds", "userId", "interviewId", "turnId", "nextAction", "routeReason",
                    "toolStatus", "evaluation", "nextQuestion", "shouldContinue"
            }) {
                keys.put(key, new ReplaceStrategy());
            }
            return keys;
        };
        try {
            return new StateGraph("InterviewTurn", strategies)
                    .addNode("evaluate_answer", node_async(this::evaluate))
                    .addNode("generate_probe", node_async(state -> generate(state, PROBE)))
                    .addNode("generate_next", node_async(state -> generate(state, SWITCH)))
                    .addNode("conclude", node_async(state -> generate(state, CONCLUDE)))
                    .addEdge(START, "evaluate_answer")
                    .addConditionalEdges("evaluate_answer", edge_async(state ->
                            normalizeAction(state.value("nextAction", String.class).orElse(SWITCH))), Map.of(
                            PROBE, "generate_probe",
                            SWITCH, "generate_next",
                            CONCLUDE, "conclude"
                    ))
                    .addEdge("generate_probe", END)
                    .addEdge("generate_next", END)
                    .addEdge("conclude", END)
                    .compile();
        } catch (GraphStateException e) {
            throw new IllegalStateException("面试流程图无法编译", e);
        }
    }

    private Map<String, Object> evaluate(OverAllState state) {
        int rounds = state.value("userRounds", Integer.class).orElse(0);
        InterviewTurnEvents listener = listenerOf(state);
        if (rounds >= MAX_USER_ROUNDS) {
            emitRoute(listener, CONCLUDE);
            return Map.of(
                    "nextAction", CONCLUDE,
                    "routeReason", "已达到最大回答轮次",
                    "toolStatus", "NONE"
            );
        }
        Long userId = state.value("userId", Long.class).orElse(null);
        Long interviewId = state.value("interviewId", Long.class).orElse(null);
        InterviewKnowledgeTools.Ledger ledger = new InterviewKnowledgeTools.Ledger();
        RouteAssistant assistant = AiServices.builder(RouteAssistant.class)
                .chatModel(chatModel)
                .toolProvider(InterviewKnowledgeTools.provider(
                        vectorStore,
                        userId,
                        interviewId,
                        minScore,
                        ledger,
                        meterRegistry,
                        status -> {
                            if (listener != null) {
                                listener.onTool(InterviewKnowledgeTools.TOOL_NAME, status);
                            }
                        }))
                .build();
        RouteDecision decision = parseDecision(assistant.decide(routeUserMessage(state)));
        if (decision == null) {
            log.warn("面试路由结果无法解析，准备重试。interviewId={}", interviewId);
            decision = parseDecision(assistant.decide(routeUserMessage(state)));
        }
        if (decision == null) {
            log.warn("面试路由结果仍然无法解析，改走 switch。interviewId={}", interviewId);
            emitRoute(listener, SWITCH);
            return Map.of("nextAction", SWITCH, "routeReason", "", "toolStatus", ledger.status());
        }
        String action = normalizeAction(decision.getNextAction());
        String reason = decision.getReason() == null ? "" : decision.getReason().strip();
        emitRoute(listener, action);
        return Map.of("nextAction", action, "routeReason", reason, "toolStatus", ledger.status());
    }

    /**
     * 只把无法解析当成路由失败。模型调用本身失败时继续抛出，不改写成 switch。
     */
    private static RouteDecision parseDecision(String raw) {
        try {
            return ModelJsonParser.read(ROUTE_MAPPER, raw, RouteDecision.class);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("无法解析")) {
                return null;
            }
            throw e;
        }
    }

    private InterviewTurnEvents listenerOf(OverAllState state) {
        return events.get(state.value("turnId", String.class).orElse(""));
    }

    private static void emitRoute(InterviewTurnEvents listener, String action) {
        if (listener != null) {
            listener.onRoute(action);
        }
    }

    private Map<String, Object> generate(OverAllState state, String action) {
        String turnId = state.value("turnId", String.class).orElse("");
        InterviewTurnEvents listener = events.get(turnId);
        if (listener != null) {
            return generateStream(state, action, listener);
        }
        InterviewAnswerAiResult result = jsonChat.complete(
                "你是专业的 IT 技术面试官。始终只返回合法 JSON，不要使用 markdown。",
                syncPrompt(state, action),
                TEMPERATURE,
                InterviewAnswerAiResult.class
        );
        if (result == null) {
            throw new RuntimeException("AI面试回答分析失败");
        }
        boolean continueInterview = !CONCLUDE.equals(action) && !Boolean.FALSE.equals(result.getShouldContinue());
        String nextQuestion = continueInterview && result.getNextQuestion() != null
                ? result.getNextQuestion().strip()
                : "";
        String evaluation = result.getEvaluation() == null ? "" : result.getEvaluation().strip();
        return Map.of(
                "evaluation", evaluation,
                "nextQuestion", nextQuestion,
                "shouldContinue", continueInterview
        );
    }

    private Map<String, Object> generateStream(OverAllState state, String action, InterviewTurnEvents listener) {
        ChatRequest request = ChatRequest.builder()
                .messages(java.util.List.of(
                        SystemMessage.from("你是专业的 IT 技术面试官。请严格按指定标记格式输出，不要使用 JSON。"),
                        UserMessage.from(streamPrompt(state, action))
                ))
                .parameters(OpenAiChatRequestParameters.builder()
                        .temperature(TEMPERATURE)
                        .build())
                .build();
        StringBuilder buffer = new StringBuilder();
        java.util.concurrent.CompletableFuture<String> completed = new java.util.concurrent.CompletableFuture<>();
        streamingChatModel.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                if (partialResponse == null || partialResponse.isEmpty()) {
                    return;
                }
                buffer.append(partialResponse);
                listener.onDelta(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                if (buffer.isEmpty() && completeResponse != null && completeResponse.aiMessage() != null
                        && completeResponse.aiMessage().text() != null
                        && !completeResponse.aiMessage().text().isEmpty()) {
                    String text = completeResponse.aiMessage().text();
                    buffer.append(text);
                    listener.onDelta(text);
                }
                completed.complete(buffer.toString());
            }

            @Override
            public void onError(Throwable error) {
                completed.completeExceptionally(error);
            }
        });
        String streamed;
        try {
            streamed = completed.get(90, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new RuntimeException("AI面试流式调用超时", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("AI面试流式调用被中断", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new RuntimeException("AI面试流式调用失败", e.getCause() == null ? e : e.getCause());
        }
        int rounds = state.value("userRounds", Integer.class).orElse(0);
        try {
            InterviewAnswerAiResult parsed = InterviewAnswerMarkers.parse(streamed, rounds);
            return answerState(parsed);
        } catch (RuntimeException markerError) {
            if (!"AI未返回下一题".equals(markerError.getMessage())) {
                throw markerError;
            }
            // 标记流已经推给前端，但没解析出下一题。再用一次 JSON 调用补齐 done，避免这一轮只剩 error。
            InterviewAnswerAiResult repaired = jsonChat.complete(
                    "你是专业的 IT 技术面试官。始终只返回合法 JSON，不要使用 markdown。",
                    syncPrompt(state, action),
                    TEMPERATURE,
                    InterviewAnswerAiResult.class
            );
            if (repaired == null) {
                throw markerError;
            }
            boolean continueInterview = !CONCLUDE.equals(action) && !Boolean.FALSE.equals(repaired.getShouldContinue());
            repaired.setShouldContinue(continueInterview);
            if (!continueInterview) {
                repaired.setNextQuestion("");
            }
            return answerState(repaired);
        }
    }

    private static Map<String, Object> answerState(InterviewAnswerAiResult result) {
        return Map.of(
                "evaluation", result.getEvaluation() == null ? "" : result.getEvaluation(),
                "nextQuestion", result.getNextQuestion() == null ? "" : result.getNextQuestion(),
                "shouldContinue", Boolean.TRUE.equals(result.getShouldContinue())
        );
    }

    private static Map<String, Object> initialState(InterviewTurnInput input, String turnId) {
        Map<String, Object> state = new HashMap<>();
        state.put("position", input.position());
        state.put("resume", input.resume());
        state.put("history", input.history());
        state.put("question", input.question());
        state.put("answer", input.answer());
        state.put("userRounds", input.userRounds());
        if (input.userId() != null) {
            state.put("userId", input.userId());
        }
        if (input.interviewId() != null) {
            state.put("interviewId", input.interviewId());
        }
        state.put("turnId", turnId);
        return state;
    }

    private static String routeUserMessage(OverAllState state) {
        return """
                岗位：%s
                当前问题：%s
                历史回答轮次：%d
                候选人回答：%s
                """.formatted(
                state.value("position", String.class).orElse(""),
                state.value("question", String.class).orElse(""),
                state.value("userRounds", Integer.class).orElse(0),
                state.value("answer", String.class).orElse("")
        );
    }

    private static String syncPrompt(OverAllState state, String action) {
        String task = switch (action) {
            case PROBE -> "生成追问。点评要指出没讲清的点，下一题必须承接当前回答。";
            case CONCLUDE -> "收束这一轮。给出结束点评，shouldContinue 必须是 false，nextQuestion 为空字符串。";
            default -> "生成下一题。点评简短，下一题换成还没覆盖的岗位相关技术，不要重复当前问题。";
        };
        return """
                你是一名严格但友好的 IT 技术面试官，岗位：%s。
                %s
                路由依据：%s
                不得虚构候选人没有的经历或技能。
                %s
                只返回 JSON：
                {"evaluation":"点评","nextQuestion":"下一题或空字符串","shouldContinue":true}

                简历摘要：
                %s

                历史对话：
                %s

                当前问题：%s
                候选人回答：%s
                """.formatted(
                state.value("position", String.class).orElse(""),
                task,
                state.value("routeReason", String.class).orElse(""),
                toolConstraint(state),
                state.value("resume", String.class).orElse(""),
                state.value("history", String.class).orElse(""),
                state.value("question", String.class).orElse(""),
                state.value("answer", String.class).orElse("")
        );
    }

    private static String streamPrompt(OverAllState state, String action) {
        String task = switch (action) {
            case PROBE -> "生成追问。下一题必须承接当前回答。";
            case CONCLUDE -> "收束这一轮。【追问】留空，【继续】写 false。";
            default -> "生成下一题。换成还没覆盖的岗位相关技术。";
        };
        return """
                你是一名严格但友好的 IT 技术面试官，岗位：%s。
                %s
                路由依据：%s
                不得虚构候选人没有的经历或技能。
                %s
                严格按照下面格式输出，不要 JSON，不要 markdown：

                【点评】
                （点评内容，可多行）
                【追问】
                （下一题；收束时留空）
                【继续】
                %s

                简历摘要：
                %s

                历史对话：
                %s

                当前问题：%s
                候选人回答：%s
                """.formatted(
                state.value("position", String.class).orElse(""),
                task,
                state.value("routeReason", String.class).orElse(""),
                toolConstraint(state),
                CONCLUDE.equals(action) ? "false" : "true",
                state.value("resume", String.class).orElse(""),
                state.value("history", String.class).orElse(""),
                state.value("question", String.class).orElse(""),
                state.value("answer", String.class).orElse("")
        );
    }

    private static String toolConstraint(OverAllState state) {
        String status = state.value("toolStatus", String.class).orElse("NONE");
        if (InterviewKnowledgeTools.STATUS_FAILED.equals(status)) {
            return "知识库工具状态：FAILED。不得声称根据知识库得出结论。";
        }
        if (InterviewKnowledgeTools.STATUS_SUCCESS.equals(status)) {
            return "知识库工具状态：SUCCESS。只能依据工具返回里 status=SUCCESS 的 sources。";
        }
        return "本轮没有调用知识库。";
    }

    private static String rootMessage(Throwable error) {
        StringBuilder builder = new StringBuilder(error.getClass().getSimpleName());
        Throwable current = error.getCause();
        int depth = 0;
        while (current != null && depth++ < 4) {
            builder.append(" -> ").append(current.getClass().getSimpleName());
            current = current.getCause();
        }
        return builder.toString();
    }

    private static boolean causedByMissingQuestion(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if ("AI未返回下一题".equals(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private interface RouteAssistant {
        @dev.langchain4j.service.SystemMessage(ROUTE_SYSTEM)
        String decide(@dev.langchain4j.service.UserMessage String userMessage);
    }

    @lombok.Data
    private static class RouteDecision {
        private Double score;
        private String nextAction;
        private String reason;
    }
}
