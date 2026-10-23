package com.aijob.server.service;

import com.aijob.server.entity.KnowledgeChunk;
import com.aijob.server.entity.KnowledgeDocument;
import com.aijob.server.vo.KnowledgeSourceVO;
import dev.langchain4j.data.embedding.Embedding;

import java.util.List;

public interface KnowledgeVectorStoreService {

    /**
     * 按 document_id + user_id 删除 Chroma 中向量（幂等）。
     */
    void deleteByDocument(Long documentId, Long userId);

    /**
     * 将 chunk 对应 embedding 写入 Chroma，使用稳定 ID：chunk-{chunkId}。
     *
     * @return 写入后的 vectorId 列表（与 chunks 顺序一致）
     */
    List<String> upsertChunks(
            KnowledgeDocument document,
            List<KnowledgeChunk> chunks,
            List<Embedding> embeddings
    );

    /**
     * 按 document_id 统计向量数量（用于验收）。
     */
    long countByDocument(Long documentId, Long userId);

    /**
     * Top-K 向量检索（强制 user_id 过滤；可选 document_id）。
     * <p>
     * {@code minScore} 对应 LangChain4j {@code EmbeddingSearchRequest#minScore}：
     * 过滤的是转换后的 relevance score（越大越相关），不是 Chroma 原始 distance。
     * 新建 collection 时 ChromaEmbeddingStore 使用 cosine distance；查询后
     * {@code distance → score}，再保留 {@code score >= minScore} 的命中。
     */
    List<KnowledgeSourceVO> searchTopK(
            String question,
            Long userId,
            Long documentId,
            int topK,
            double minScore
    );
}
