package com.aijob.server.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSourceVO {

    private Long documentId;

    private String title;

    private Long chunkId;

    private String snippet;

    /**
     * LangChain4j {@code EmbeddingMatch#score()}：相关性分数，范围约 [0,1]，越大越相关。
     * <p>
     * 注意：Chroma 查询原始字段是 {@code distances}（越小越相似）；框架在
     * {@code ChromaEmbeddingStore} 内转为本 score 后再返回，业务侧不要按 distance 解读。
     */
    private Double score;
}
