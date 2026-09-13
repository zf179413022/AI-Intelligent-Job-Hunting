package com.aijob.server.service.impl;

import com.aijob.server.exception.ForbiddenException;

import com.aijob.server.entity.KnowledgeChunk;
import com.aijob.server.entity.KnowledgeDocument;
import com.aijob.server.mapper.KnowledgeChunkMapper;
import com.aijob.server.mapper.KnowledgeDocumentMapper;
import com.aijob.server.service.KnowledgeIngestService;
import com.aijob.server.service.KnowledgeVectorStoreService;
import com.aijob.server.util.DocumentTextExtractor;
import com.aijob.server.util.KnowledgeTextChunker;
import com.aijob.server.vo.KnowledgeIngestVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeIngestServiceImpl implements KnowledgeIngestService {

    private static final String EMBEDDING_MODEL_NAME =
            "AllMiniLmL6V2QuantizedEmbeddingModel";

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final EmbeddingModel embeddingModel;
    private final KnowledgeVectorStoreService knowledgeVectorStoreService;

    @Value("${knowledge.chroma.collection-name}")
    private String chromaCollectionName;

    @Override
    @Transactional
    public KnowledgeIngestVO chunkAndEmbed(Long documentId, Long userId) {
        KnowledgeDocument document = requireOwned(documentId, userId);

        if (!"PARSED".equals(document.getStatus())
                && !"CHUNKED".equals(document.getStatus())
                && !"EMBEDDED".equals(document.getStatus())
                && !"READY".equals(document.getStatus())
                && !"FAILED".equals(document.getStatus())) {
            throw new RuntimeException(
                    "仅 PARSED/CHUNKED/EMBEDDED/READY/FAILED 状态可入库，当前：" + document.getStatus()
            );
        }

        Path path = Paths.get(document.getFilePath());
        String text;
        try {
            text = DocumentTextExtractor.extract(path, document.getFileType());
        } catch (Exception e) {
            fail(document, "切分前重新解析文档失败：" + safeMsg(e));
            throw new RuntimeException("切分失败：无法读取文档文本", e);
        }

        if (text == null || text.isBlank()) {
            fail(document, "文档文本为空，无法切分");
            throw new RuntimeException("文档文本为空，无法切分");
        }

        List<String> parts = KnowledgeTextChunker.chunk(text);
        if (parts.isEmpty()) {
            throw new RuntimeException("未生成任何 chunk");
        }

        // 幂等：清 MySQL 旧 chunk + 清 Chroma 旧向量
        knowledgeVectorStoreService.deleteByDocument(document.getId(), userId);
        knowledgeChunkMapper.delete(
                new LambdaQueryWrapper<KnowledgeChunk>()
                        .eq(KnowledgeChunk::getDocumentId, document.getId())
                        .eq(KnowledgeChunk::getUserId, userId)
        );

        List<KnowledgeChunk> saved = new ArrayList<>();
        int index = 0;
        for (String part : parts) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setDocumentId(document.getId());
            chunk.setUserId(userId);
            chunk.setChunkIndex(index++);
            chunk.setContent(part);
            chunk.setTokenEstimate(part.length());
            chunk.setVectorId(null);
            knowledgeChunkMapper.insert(chunk);
            saved.add(chunk);
        }

        // 状态：CHUNKED
        document.setStatus("CHUNKED");
        document.setChunkCount(saved.size());
        document.setErrorMessage(null);
        knowledgeDocumentMapper.updateById(document);

        int dimension;
        int sampleLen;
        List<Embedding> embeddings;
        List<String> vectorIds;
        try {
            List<TextSegment> segments = saved.stream()
                    .map(c -> TextSegment.from(c.getContent()))
                    .toList();
            Response<List<Embedding>> all = embeddingModel.embedAll(segments);
            embeddings = all.content();
            if (embeddings == null || embeddings.size() != saved.size()) {
                throw new RuntimeException("Embedding 数量与 chunk 不一致");
            }

            dimension = embeddings.getFirst().dimension();
            sampleLen = embeddings.getFirst().vector().length;

            vectorIds = knowledgeVectorStoreService.upsertChunks(document, saved, embeddings);

            for (int i = 0; i < saved.size(); i++) {
                KnowledgeChunk chunk = saved.get(i);
                chunk.setVectorId(vectorIds.get(i));
                knowledgeChunkMapper.updateById(chunk);
            }
        } catch (Exception e) {
            fail(document, "Embedding/Chroma 写入失败：" + safeMsg(e));
            throw new RuntimeException("LangChain4j Embedding/Chroma 失败：" + e.getMessage(), e);
        }

        // 状态：EMBEDDED → READY
        document.setStatus("EMBEDDED");
        knowledgeDocumentMapper.updateById(document);

        document.setStatus("READY");
        document.setErrorMessage(null);
        knowledgeDocumentMapper.updateById(document);

        long chromaCount = knowledgeVectorStoreService.countByDocument(document.getId(), userId);

        KnowledgeDocument latest = knowledgeDocumentMapper.selectById(document.getId());
        return new KnowledgeIngestVO(
                latest,
                saved.size(),
                EMBEDDING_MODEL_NAME,
                dimension,
                sampleLen,
                chromaCollectionName,
                chromaCount,
                vectorIds.isEmpty() ? null : vectorIds.getFirst()
        );
    }

    @Override
    public List<KnowledgeChunk> listChunks(Long documentId, Long userId) {
        requireOwned(documentId, userId);
        return knowledgeChunkMapper.selectList(
                new LambdaQueryWrapper<KnowledgeChunk>()
                        .eq(KnowledgeChunk::getDocumentId, documentId)
                        .eq(KnowledgeChunk::getUserId, userId)
                        .orderByAsc(KnowledgeChunk::getChunkIndex)
        );
    }

    private void fail(KnowledgeDocument document, String message) {
        document.setStatus("FAILED");
        document.setErrorMessage(message);
        knowledgeDocumentMapper.updateById(document);
    }

    private KnowledgeDocument requireOwned(Long id, Long userId) {
        KnowledgeDocument document = knowledgeDocumentMapper.selectById(id);
        if (document == null) {
            throw new RuntimeException("知识库文档不存在");
        }
        if (!document.getUserId().equals(userId)) {
            throw new ForbiddenException("无权访问该知识库文档");
        }
        return document;
    }

    private String safeMsg(Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return message.length() > 480 ? message.substring(0, 480) : message;
    }
}
