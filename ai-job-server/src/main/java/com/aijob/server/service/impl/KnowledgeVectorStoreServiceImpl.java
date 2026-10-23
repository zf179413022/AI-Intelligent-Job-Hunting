package com.aijob.server.service.impl;

import com.aijob.server.entity.KnowledgeChunk;
import com.aijob.server.entity.KnowledgeDocument;
import com.aijob.server.mapper.KnowledgeDocumentMapper;
import com.aijob.server.service.KnowledgeVectorStoreService;
import com.aijob.server.vo.KnowledgeSourceVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Service
@RequiredArgsConstructor
public class KnowledgeVectorStoreServiceImpl implements KnowledgeVectorStoreService {

    private final EmbeddingStore<TextSegment> knowledgeEmbeddingStore;
    private final EmbeddingModel embeddingModel;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Override
    public void deleteByDocument(Long documentId, Long userId) {
        Filter filter = metadataKey("document_id").isEqualTo(String.valueOf(documentId))
                .and(metadataKey("user_id").isEqualTo(String.valueOf(userId)));
        knowledgeEmbeddingStore.removeAll(filter);
    }

    @Override
    public List<String> upsertChunks(
            KnowledgeDocument document,
            List<KnowledgeChunk> chunks,
            List<Embedding> embeddings) {

        if (chunks.size() != embeddings.size()) {
            throw new RuntimeException("chunk 与 embedding 数量不一致");
        }

        // 幂等：先删该文档旧向量，再按稳定 ID 写入（分批）
        deleteByDocument(document.getId(), document.getUserId());

        List<String> allIds = new ArrayList<>(chunks.size());
        final int batchSize = 8;
        for (int start = 0; start < chunks.size(); start += batchSize) {
            int end = Math.min(start + batchSize, chunks.size());
            List<KnowledgeChunk> batchChunks = chunks.subList(start, end);
            List<Embedding> batchEmbeddings = embeddings.subList(start, end);

            List<String> ids = new ArrayList<>(batchChunks.size());
            List<TextSegment> segments = new ArrayList<>(batchChunks.size());
            for (KnowledgeChunk chunk : batchChunks) {
                String vectorId = stableVectorId(chunk.getId());
                ids.add(vectorId);
                Metadata metadata = new Metadata();
                metadata.put("chunk_id", String.valueOf(chunk.getId()));
                metadata.put("document_id", String.valueOf(document.getId()));
                metadata.put("user_id", String.valueOf(document.getUserId()));
                segments.add(TextSegment.from(chunk.getContent(), metadata));
            }
            knowledgeEmbeddingStore.addAll(ids, batchEmbeddings, segments);
            allIds.addAll(ids);
        }
        return allIds;
    }

    @Override
    public long countByDocument(Long documentId, Long userId) {
        float[] zeros = new float[384];
        Embedding query = new Embedding(zeros);
        Filter filter = metadataKey("document_id").isEqualTo(String.valueOf(documentId))
                .and(metadataKey("user_id").isEqualTo(String.valueOf(userId)));

        EmbeddingSearchResult<TextSegment> result = knowledgeEmbeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(query)
                        .maxResults(10_000)
                        .minScore(0.0)
                        .filter(filter)
                        .build()
        );
        return result.matches() == null ? 0 : result.matches().size();
    }

    @Override
    public List<KnowledgeSourceVO> searchTopK(
            String question,
            Long userId,
            Long documentId,
            int topK,
            double minScore) {

        if (!StringUtils.hasText(question)) {
            throw new RuntimeException("question 不能为空");
        }
        if (userId == null) {
            throw new RuntimeException("userId 不能为空");
        }

        Embedding queryEmbedding = embeddingModel.embed(question.trim()).content();

        Filter filter = metadataKey("user_id").isEqualTo(String.valueOf(userId));
        if (documentId != null) {
            filter = filter.and(metadataKey("document_id").isEqualTo(String.valueOf(documentId)));
        }

        EmbeddingSearchResult<TextSegment> result = knowledgeEmbeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(Math.max(1, topK))
                        .minScore(Math.max(0.0, minScore))
                        .filter(filter)
                        .build()
        );

        List<EmbeddingMatch<TextSegment>> matches =
                result.matches() == null ? List.of() : result.matches();

        Set<Long> docIds = matches.stream()
                .map(m -> parseLong(m.embedded() == null ? null : m.embedded().metadata().getString("document_id")))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> titleByDocId = loadTitles(docIds, userId);

        List<KnowledgeSourceVO> sources = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : matches) {
            TextSegment segment = match.embedded();
            if (segment == null) {
                continue;
            }
            Metadata md = segment.metadata();
            Long chunkId = parseLong(md.getString("chunk_id"));
            Long docId = parseLong(md.getString("document_id"));
            String owner = md.getString("user_id");
            // 双重校验：metadata user_id 必须等于当前用户
            if (owner == null || !owner.equals(String.valueOf(userId))) {
                continue;
            }

            String text = segment.text() == null ? "" : segment.text();
            String snippet = text.length() > 240 ? text.substring(0, 240) + "..." : text;

            sources.add(new KnowledgeSourceVO(
                    docId,
                    titleByDocId.getOrDefault(docId, "未知文档"),
                    chunkId,
                    snippet,
                    match.score()
            ));
        }
        return sources;
    }

    private Map<Long, String> loadTitles(Set<Long> docIds, Long userId) {
        Map<Long, String> map = new HashMap<>();
        if (docIds == null || docIds.isEmpty()) {
            return map;
        }
        List<KnowledgeDocument> docs = knowledgeDocumentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getUserId, userId)
                        .in(KnowledgeDocument::getId, docIds)
        );
        for (KnowledgeDocument doc : docs) {
            map.put(doc.getId(), doc.getTitle());
        }
        return map;
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String stableVectorId(Long chunkId) {
        return "chunk-" + chunkId;
    }
}
