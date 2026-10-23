package com.aijob.server.service;

import com.aijob.server.entity.KnowledgeChunk;
import com.aijob.server.vo.KnowledgeIngestVO;

import java.util.List;

public interface KnowledgeIngestService {

    /**
     * PARSED → 切分写入 knowledge_chunk → EmbeddingModel 生成向量（不落库）→ CHUNKED。
     * 重复执行会先删除该文档已有 chunk，再重建。
     */
    KnowledgeIngestVO chunkAndEmbed(Long documentId, Long userId);

    List<KnowledgeChunk> listChunks(Long documentId, Long userId);
}
