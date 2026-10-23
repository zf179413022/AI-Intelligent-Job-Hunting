package com.aijob.server.ai;

import com.aijob.server.service.KnowledgeVectorStoreService;
import com.aijob.server.vo.KnowledgeSourceVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Consumer;

/**
 * 面试评分前的知识库工具。
 * 模型只能传 query；userId 由调用方闭包绑定。执行结果以 JSON 返回，由 LangChain4j 送回当前对话，不在这里决定 nextAction。
 */
@Slf4j
public final class InterviewKnowledgeTools {

    static final String TOOL_NAME = "searchKnowledgeBase";

    static final String STATUS_START = "START";

    static final String STATUS_SUCCESS = "SUCCESS";

    static final String STATUS_FAILED = "FAILED";

    static final int QUERY_MAX_LENGTH = 80;

    static final int MAX_CALLS = 2;

    static final String UNAVAILABLE = "无法对照知识库";

    private static final int TOP_K = 3;

    private static final int SNIPPET_LIMIT = 180;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String FAILED_JSON = """
            {"status":"FAILED","message":"无法对照知识库"}""";

    private static final ToolSpecification SPECIFICATION = ToolSpecification.builder()
            .name(TOOL_NAME)
            .description("检索当前用户知识库里与某个技术点相关的资料。只传 query。返回 JSON：status=SUCCESS 时 sources 才可作为依据；status=FAILED 时不得声称根据知识库。")
            .parameters(JsonObjectSchema.builder()
                    .addStringProperty("query", "要检索的技术点，尽量短")
                    .required("query")
                    .build())
            .build();

    private InterviewKnowledgeTools() {
    }

    public static ToolProvider provider(
            KnowledgeVectorStoreService vectorStore,
            Long userId,
            Long interviewId,
            double minScore,
            Ledger ledger,
            MeterRegistry meterRegistry,
            Consumer<String> toolStatus) {
        if (userId == null) {
            return request -> ToolProviderResult.builder().build();
        }
        ToolExecutor executor = (request, memoryId) -> execute(
                vectorStore, userId, interviewId, queryOf(request), minScore, ledger, meterRegistry, toolStatus
        );
        return request -> ToolProviderResult.builder()
                .add(SPECIFICATION, executor)
                .build();
    }

    /**
     * 只接受 query。超长、超次数直接失败，不访问 Chroma。检索异常只重试一次，仍失败则返回固定 JSON，不把异常文本交给模型。
     */
    static String execute(
            KnowledgeVectorStoreService vectorStore,
            Long userId,
            Long interviewId,
            String query,
            double minScore,
            Ledger ledger,
            MeterRegistry meterRegistry,
            Consumer<String> toolStatus) {
        long started = System.nanoTime();
        ledger.markInvoked();
        emit(toolStatus, STATUS_START);
        int retryCount = 0;
        try {
            if (ledger.calls() > MAX_CALLS) {
                return fail(ledger, interviewId, started, retryCount, meterRegistry, toolStatus, "CALL_LIMIT");
            }
            if (query == null || query.isBlank() || query.strip().length() > QUERY_MAX_LENGTH) {
                return fail(ledger, interviewId, started, retryCount, meterRegistry, toolStatus, "INVALID_QUERY");
            }
            String normalized = query.strip();
            try {
                String result = success(vectorStore, userId, normalized, minScore);
                return succeed(ledger, interviewId, started, retryCount, meterRegistry, toolStatus, result);
            } catch (RuntimeException first) {
                retryCount = 1;
                ledger.setRetryCount(retryCount);
                log.warn("面试知识库工具准备重试, interviewId={}, tool={}, error={}",
                        interviewId, TOOL_NAME, first.getClass().getSimpleName());
                try {
                    String result = success(vectorStore, userId, normalized, minScore);
                    return succeed(ledger, interviewId, started, retryCount, meterRegistry, toolStatus, result);
                } catch (RuntimeException second) {
                    return fail(ledger, interviewId, started, retryCount, meterRegistry, toolStatus,
                            second.getClass().getSimpleName());
                }
            }
        } catch (RuntimeException unexpected) {
            return fail(ledger, interviewId, started, retryCount, meterRegistry, toolStatus,
                    unexpected.getClass().getSimpleName());
        }
    }

    static String search(
            KnowledgeVectorStoreService vectorStore,
            Long userId,
            String query,
            double minScore) {
        List<KnowledgeSourceVO> hits = vectorStore.searchTopK(query, userId, null, TOP_K, minScore);
        return successJson(hits);
    }

    /**
     * 一轮评分内的工具记账。失败后仍把固定 JSON 交回模型，由模型决定 nextAction。
     */
    public static final class Ledger {
        private int calls;
        private boolean invoked;
        private boolean failed;
        private int retryCount;

        void markInvoked() {
            invoked = true;
            calls++;
        }

        void markFailed() {
            failed = true;
        }

        void setRetryCount(int retryCount) {
            this.retryCount = retryCount;
        }

        int calls() {
            return calls;
        }

        public int retryCount() {
            return retryCount;
        }

        public boolean failed() {
            return failed;
        }

        public String status() {
            if (failed) {
                return STATUS_FAILED;
            }
            if (invoked) {
                return STATUS_SUCCESS;
            }
            return "NONE";
        }
    }

    private static String success(
            KnowledgeVectorStoreService vectorStore,
            Long userId,
            String query,
            double minScore) {
        return search(vectorStore, userId, query, minScore);
    }

    private static String succeed(
            Ledger ledger,
            Long interviewId,
            long started,
            int retryCount,
            MeterRegistry meterRegistry,
            Consumer<String> toolStatus,
            String result) {
        ledger.setRetryCount(retryCount);
        record(meterRegistry, STATUS_SUCCESS, started, retryCount);
        log.info("面试知识库工具完成, interviewId={}, tool={}, status={}, durationMs={}, retryCount={}",
                interviewId, TOOL_NAME, STATUS_SUCCESS, elapsedMs(started), retryCount);
        emit(toolStatus, STATUS_SUCCESS);
        return result;
    }

    private static String fail(
            Ledger ledger,
            Long interviewId,
            long started,
            int retryCount,
            MeterRegistry meterRegistry,
            Consumer<String> toolStatus,
            String error) {
        ledger.markFailed();
        ledger.setRetryCount(retryCount);
        record(meterRegistry, STATUS_FAILED, started, retryCount);
        log.warn("面试知识库工具失败, interviewId={}, tool={}, status={}, durationMs={}, retryCount={}, error={}",
                interviewId, TOOL_NAME, STATUS_FAILED, elapsedMs(started), retryCount, error);
        emit(toolStatus, STATUS_FAILED);
        return FAILED_JSON;
    }

    private static void record(MeterRegistry meterRegistry, String status, long started, int retryCount) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.timer("ai.tool.duration", "tool", TOOL_NAME, "status", status)
                .record(java.time.Duration.ofNanos(Math.max(0, System.nanoTime() - started)));
        meterRegistry.counter("ai.tool.calls", "tool", TOOL_NAME, "status", status, "retry", Integer.toString(retryCount))
                .increment();
    }

    private static long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    private static void emit(Consumer<String> toolStatus, String status) {
        if (toolStatus != null) {
            toolStatus.accept(status);
        }
    }

    private static String successJson(List<KnowledgeSourceVO> hits) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("status", STATUS_SUCCESS);
        ArrayNode sources = root.putArray("sources");
        if (hits != null) {
            for (KnowledgeSourceVO hit : hits) {
                ObjectNode item = sources.addObject();
                String content = hit.getSnippet() == null ? "" : hit.getSnippet().strip();
                if (content.length() > SNIPPET_LIMIT) {
                    content = content.substring(0, SNIPPET_LIMIT);
                }
                item.put("content", content);
                if (hit.getScore() != null) {
                    item.put("score", hit.getScore());
                }
            }
        }
        try {
            return MAPPER.writeValueAsString(root);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return FAILED_JSON;
        }
    }

    private static String queryOf(ToolExecutionRequest request) {
        if (request == null || request.arguments() == null) {
            return "";
        }
        try {
            JsonNode node = MAPPER.readTree(request.arguments());
            return node.path("query").asText("");
        } catch (Exception e) {
            return "";
        }
    }
}
