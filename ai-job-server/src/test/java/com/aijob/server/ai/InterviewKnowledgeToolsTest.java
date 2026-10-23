package com.aijob.server.ai;

import com.aijob.server.entity.KnowledgeChunk;
import com.aijob.server.entity.KnowledgeDocument;
import com.aijob.server.service.KnowledgeVectorStoreService;
import com.aijob.server.vo.KnowledgeSourceVO;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewKnowledgeToolsTest {

    @Test
    void offersTheToolWithoutAKeywordWhitelist() {
        ToolProviderResult result = InterviewKnowledgeTools.provider(
                new StubStore(), 9L, 3L, 0.2, new InterviewKnowledgeTools.Ledger(), null, null
        ).provideTools(null);

        assertEquals(1, result.tools().size());
        ToolSpecification spec = result.tools().keySet().iterator().next();
        assertEquals("searchKnowledgeBase", spec.name());
        String schema = spec.parameters().toString().toLowerCase();
        assertTrue(schema.contains("query"));
        assertFalse(schema.contains("userid"));
        assertFalse(schema.contains("interviewid"));
    }

    @Test
    void doesNotOfferTheToolWhenTheUserIsMissing() {
        ToolProviderResult result = InterviewKnowledgeTools.provider(
                new StubStore(), null, 3L, 0.2, new InterviewKnowledgeTools.Ledger(), null, null
        ).provideTools(null);

        assertTrue(result.tools().isEmpty());
    }

    @Test
    void bindsUserIdFromTheServerWhenTheModelSendsAnotherUser() {
        AtomicLong seenUser = new AtomicLong(-1);
        ToolProviderResult result = InterviewKnowledgeTools.provider(
                new RecordingStore(seenUser), 9L, 3L, 0.2, new InterviewKnowledgeTools.Ledger(), null, null
        ).provideTools(null);
        ToolExecutor executor = result.toolExecutorByName("searchKnowledgeBase");

        String text = executor.execute(
                ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("searchKnowledgeBase")
                        .arguments("{\"userId\":2,\"query\":\"Redis\"}")
                        .build(),
                "ignored"
        );

        assertEquals(9L, seenUser.get());
        assertTrue(text.contains("\"status\":\"SUCCESS\""));
        assertTrue(text.contains("过期键"));
        assertFalse(text.contains("\"userId\""));
    }

    @Test
    void retriesOnceAndThenSucceeds() {
        InterviewKnowledgeTools.Ledger ledger = new InterviewKnowledgeTools.Ledger();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FlakyStore store = new FlakyStore();

        String text = InterviewKnowledgeTools.execute(
                store, 9L, 3L, "Redis", 0.2, ledger, registry, null
        );

        assertEquals(2, store.calls);
        assertEquals(1, ledger.retryCount());
        assertEquals("SUCCESS", ledger.status());
        assertTrue(text.contains("\"status\":\"SUCCESS\""));
        assertFalse(text.contains("secret"));
        assertEquals(1.0, registry.counter("ai.tool.calls", "tool", "searchKnowledgeBase", "status", "SUCCESS", "retry", "1").count());
    }

    @Test
    void returnsFixedFailureWhenBothAttemptsThrow() {
        InterviewKnowledgeTools.Ledger ledger = new InterviewKnowledgeTools.Ledger();
        String text = InterviewKnowledgeTools.execute(
                new ExplodingStore(), 9L, 3L, "Redis", 0.2, ledger, null, null
        );

        assertEquals(InterviewKnowledgeTools.STATUS_FAILED, ledger.status());
        assertEquals(1, ledger.retryCount());
        assertTrue(text.contains("\"status\":\"FAILED\""));
        assertTrue(text.contains("无法对照知识库"));
        assertFalse(text.contains("jdbc"));
        assertFalse(text.contains("password"));
        assertFalse(text.contains("Exception"));
    }

    @Test
    void rejectsOversizedQueryAndDoesNotSearch() {
        InterviewKnowledgeTools.Ledger ledger = new InterviewKnowledgeTools.Ledger();
        String text = InterviewKnowledgeTools.execute(
                new ExplodingStore(), 9L, 3L, "R".repeat(81), 0.2, ledger, null, null
        );

        assertTrue(text.contains("\"status\":\"FAILED\""));
        assertEquals("FAILED", ledger.status());
        assertEquals(0, ledger.retryCount());
    }

    @Test
    void stopsAfterTheCallLimit() {
        InterviewKnowledgeTools.Ledger ledger = new InterviewKnowledgeTools.Ledger();
        InterviewKnowledgeTools.execute(new StubStore(), 9L, 3L, "Redis", 0.2, ledger, null, null);
        InterviewKnowledgeTools.execute(new StubStore(), 9L, 3L, "Redis", 0.2, ledger, null, null);
        String third = InterviewKnowledgeTools.execute(new StubStore(), 9L, 3L, "Redis", 0.2, ledger, null, null);

        assertTrue(third.contains("\"status\":\"FAILED\""));
        assertEquals("FAILED", ledger.status());
    }

    @Test
    void tellsTheModelWhenNothingIsFound() {
        String text = InterviewKnowledgeTools.search(new StubStore(), 9L, "不存在的主题", 0.2);

        assertTrue(text.contains("\"status\":\"SUCCESS\""));
        assertTrue(text.contains("\"sources\":[]"));
    }

    private static class StubStore implements KnowledgeVectorStoreService {
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
            if (!Long.valueOf(9L).equals(userId) || question == null || !question.contains("Redis")) {
                return List.of();
            }
            return List.of(new KnowledgeSourceVO(1L, "Redis 笔记", 2L, "过期键会在惰性删除或定期删除时清掉", 0.9));
        }
    }

    private static final class RecordingStore extends StubStore {
        private final AtomicLong seenUser;

        private RecordingStore(AtomicLong seenUser) {
            this.seenUser = seenUser;
        }

        @Override
        public List<KnowledgeSourceVO> searchTopK(String question, Long userId, Long documentId, int topK, double minScore) {
            seenUser.set(userId);
            return super.searchTopK(question, userId, documentId, topK, minScore);
        }
    }

    private static final class FlakyStore extends StubStore {
        private int calls;

        @Override
        public List<KnowledgeSourceVO> searchTopK(String question, Long userId, Long documentId, int topK, double minScore) {
            calls++;
            if (calls == 1) {
                throw new RuntimeException("chroma timeout at /secret/path");
            }
            return super.searchTopK(question, userId, documentId, topK, minScore);
        }
    }

    private static final class ExplodingStore extends StubStore {
        @Override
        public List<KnowledgeSourceVO> searchTopK(String question, Long userId, Long documentId, int topK, double minScore) {
            throw new RuntimeException("jdbc:mysql://root:password@internal-db/job");
        }
    }
}
