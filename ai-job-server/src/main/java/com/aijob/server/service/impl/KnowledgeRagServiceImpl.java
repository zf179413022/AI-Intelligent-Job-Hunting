package com.aijob.server.service.impl;

import com.aijob.server.exception.ForbiddenException;

import com.aijob.server.ai.KnowledgeRagAssistant;
import com.aijob.server.ai.KnowledgeStreamingRagAssistant;
import com.aijob.server.dto.KnowledgeAskRequest;
import com.aijob.server.entity.KnowledgeChunk;
import com.aijob.server.entity.KnowledgeDocument;
import com.aijob.server.entity.KnowledgeQa;
import com.aijob.server.mapper.KnowledgeChunkMapper;
import com.aijob.server.mapper.KnowledgeDocumentMapper;
import com.aijob.server.mapper.KnowledgeQaMapper;
import com.aijob.server.service.KnowledgeRagService;
import com.aijob.server.service.KnowledgeVectorStoreService;
import com.aijob.server.vo.KnowledgeAskVO;
import com.aijob.server.vo.KnowledgeRetrieveVO;
import com.aijob.server.vo.KnowledgeSourceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeRagServiceImpl implements KnowledgeRagService {

    private static final String PROVIDER = "langchain4j-rag/chroma+deepseek";
    private static final String PROVIDER_STREAM = "langchain4j-rag/chroma+deepseek-stream";

    private final KnowledgeVectorStoreService knowledgeVectorStoreService;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeQaMapper knowledgeQaMapper;
    private final KnowledgeRagAssistant knowledgeRagAssistant;
    private final KnowledgeStreamingRagAssistant knowledgeStreamingRagAssistant;

    @Value("${knowledge.rag.top-k:5}")
    private int defaultTopK;

    @Value("${knowledge.rag.min-score:0.2}")
    private double minScore;

    @Value("${langchain4j.open-ai.chat-model.api-key:}")
    private String apiKey;

    @Override
    public KnowledgeRetrieveVO retrieve(KnowledgeAskRequest request, Long userId) {
        String question = requireQuestion(request);
        int topK = resolveTopK(request);
        Long documentId = requireOwnedDocumentIfPresent(request.getDocumentId(), userId);

        List<KnowledgeSourceVO> sources = knowledgeVectorStoreService.searchTopK(
                question, userId, documentId, topK, minScore
        );

        return new KnowledgeRetrieveVO(question, topK, sources.size(), sources);
    }

    @Override
    public KnowledgeAskVO ask(KnowledgeAskRequest request, Long userId) {
        requireApiKey();

        KnowledgeRetrieveVO retrieved = retrieve(request, userId);
        Map<Long, String> fullTexts = loadOwnedChunkTexts(retrieved.getSources(), userId);
        String prompt = buildRagPrompt(retrieved.getQuestion(), retrieved.getSources(), fullTexts);

        String answer;
        try {
            answer = knowledgeRagAssistant.answer(prompt);
        } catch (Exception e) {
            throw new RuntimeException("DeepSeek RAG 生成失败：" + e.getMessage(), e);
        }
        if (!StringUtils.hasText(answer)) {
            throw new RuntimeException("DeepSeek 未返回有效内容");
        }
        answer = answer.trim();

        return persistAndBuildVo(retrieved, answer, PROVIDER, userId);
    }

    @Override
    public void askStream(KnowledgeAskRequest request, Long userId, SseEmitter emitter) {
        try {
            requireApiKey();
            KnowledgeRetrieveVO retrieved = retrieve(request, userId);
            Map<Long, String> fullTexts = loadOwnedChunkTexts(retrieved.getSources(), userId);
            String prompt = buildRagPrompt(retrieved.getQuestion(), retrieved.getSources(), fullTexts);

            // 先推送检索结果，便于前端边流式显示答案边展示 Sources
            emitter.send(SseEmitter.event().name("meta").data(Map.of(
                    "question", retrieved.getQuestion(),
                    "topK", retrieved.getTopK(),
                    "hitCount", retrieved.getHitCount(),
                    "sources", retrieved.getSources() == null ? List.of() : retrieved.getSources()
            )));

            StringBuilder answerBuf = new StringBuilder();
            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            knowledgeStreamingRagAssistant.answer(prompt)
                    .onPartialResponse(token -> {
                        if (!StringUtils.hasText(token)) {
                            return;
                        }
                        answerBuf.append(token);
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("delta")
                                    .data(Map.of("content", token)));
                        } catch (IOException e) {
                            errorRef.compareAndSet(null, e);
                            throw new RuntimeException(e);
                        }
                    })
                    .onCompleteResponse(response -> {
                        try {
                            String answer = answerBuf.toString().trim();
                            if (!StringUtils.hasText(answer) && response != null
                                    && response.aiMessage() != null
                                    && StringUtils.hasText(response.aiMessage().text())) {
                                answer = response.aiMessage().text().trim();
                            }
                            if (!StringUtils.hasText(answer)) {
                                sendSseError(emitter, "DeepSeek 未返回有效内容");
                                return;
                            }
                            KnowledgeAskVO vo = persistAndBuildVo(
                                    retrieved, answer, PROVIDER_STREAM, userId
                            );
                            emitter.send(SseEmitter.event().name("done").data(vo));
                            emitter.complete();
                        } catch (Exception e) {
                            errorRef.compareAndSet(null, e);
                            sendSseError(emitter, e.getMessage() == null ? "流式 RAG 完成处理失败" : e.getMessage());
                        } finally {
                            latch.countDown();
                        }
                    })
                    .onError(error -> {
                        errorRef.compareAndSet(null, error);
                        sendSseError(emitter, error.getMessage() == null ? "流式 RAG 失败" : error.getMessage());
                        latch.countDown();
                    })
                    .start();

            // 阻塞当前异步任务线程，直到流结束（避免线程过早返回导致上下文丢失）
            boolean finished = latch.await(170, TimeUnit.SECONDS);
            if (!finished) {
                sendSseError(emitter, "流式 RAG 超时");
            }
            if (errorRef.get() != null && !(errorRef.get() instanceof IOException)) {
                // 已通过 SSE error 事件告知前端
            }
        } catch (Exception e) {
            sendSseError(emitter, e.getMessage() == null ? "流式 RAG 失败" : e.getMessage());
        }
    }

    private KnowledgeAskVO persistAndBuildVo(
            KnowledgeRetrieveVO retrieved,
            String answer,
            String provider,
            Long userId) {
        KnowledgeQa qa = new KnowledgeQa();
        qa.setUserId(userId);
        qa.setQuestion(retrieved.getQuestion());
        qa.setAnswer(answer);
        qa.setSourceChunkIds(toChunkIdJson(retrieved.getSources()));
        knowledgeQaMapper.insert(qa);

        return new KnowledgeAskVO(
                retrieved.getQuestion(),
                answer,
                retrieved.getSources(),
                retrieved.getTopK(),
                retrieved.getHitCount(),
                provider,
                qa.getId()
        );
    }

    private void sendSseError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(Map.of("message", message == null ? "流式 RAG 失败" : message)));
            emitter.complete();
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
    }

    private void requireApiKey() {
        if (!StringUtils.hasText(apiKey)) {
            throw new RuntimeException("未配置 DEEPSEEK_API_KEY，无法进行 RAG 问答");
        }
    }

    private Map<Long, String> loadOwnedChunkTexts(List<KnowledgeSourceVO> sources, Long userId) {
        Map<Long, String> map = new HashMap<>();
        if (sources == null || sources.isEmpty()) {
            return map;
        }
        List<Long> ids = sources.stream()
                .map(KnowledgeSourceVO::getChunkId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return map;
        }
        List<KnowledgeChunk> chunks = knowledgeChunkMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeChunk>()
                        .eq(KnowledgeChunk::getUserId, userId)
                        .in(KnowledgeChunk::getId, ids)
        );
        for (KnowledgeChunk chunk : chunks) {
            if (chunk.getUserId() != null && chunk.getUserId().equals(userId)) {
                map.put(chunk.getId(), chunk.getContent());
            }
        }
        return map;
    }

    private String buildRagPrompt(
            String question,
            List<KnowledgeSourceVO> sources,
            Map<Long, String> fullTexts) {
        StringBuilder sb = new StringBuilder();
        sb.append("【检索到的资料】\n");
        if (sources == null || sources.isEmpty()) {
            sb.append("（无相关资料）\n");
        } else {
            for (int i = 0; i < sources.size(); i++) {
                KnowledgeSourceVO s = sources.get(i);
                String body = fullTexts.getOrDefault(s.getChunkId(), s.getSnippet());
                sb.append("--- 资料 ").append(i + 1).append(" ---\n");
                sb.append("文档: ").append(s.getTitle()).append(" (documentId=")
                        .append(s.getDocumentId()).append(", chunkId=")
                        .append(s.getChunkId()).append(")\n");
                sb.append(body == null ? "" : body).append("\n\n");
            }
        }
        sb.append("【用户问题】\n");
        sb.append(question).append("\n");
        sb.append("\n请仅根据以上资料作答。");
        return sb.toString();
    }

    private String toChunkIdJson(List<KnowledgeSourceVO> sources) {
        if (sources == null || sources.isEmpty()) {
            return "[]";
        }
        return sources.stream()
                .map(KnowledgeSourceVO::getChunkId)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private String requireQuestion(KnowledgeAskRequest request) {
        if (request == null || !StringUtils.hasText(request.getQuestion())) {
            throw new RuntimeException("question 不能为空");
        }
        return request.getQuestion().trim();
    }

    private int resolveTopK(KnowledgeAskRequest request) {
        if (request.getTopK() != null) {
            return request.getTopK();
        }
        return defaultTopK;
    }

    private Long requireOwnedDocumentIfPresent(Long documentId, Long userId) {
        if (documentId == null) {
            return null;
        }
        KnowledgeDocument document = knowledgeDocumentMapper.selectById(documentId);
        if (document == null) {
            throw new RuntimeException("知识库文档不存在");
        }
        if (!document.getUserId().equals(userId)) {
            throw new ForbiddenException("无权访问该知识库文档");
        }
        if (!"READY".equals(document.getStatus()) && !"EMBEDDED".equals(document.getStatus())) {
            throw new RuntimeException("文档尚未完成向量入库，当前状态：" + document.getStatus());
        }
        return documentId;
    }
}
