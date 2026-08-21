package com.aijob.server.vo;

import com.aijob.server.entity.KnowledgeDocument;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeIngestVO {

    private KnowledgeDocument document;

    private Integer chunkCount;

    private String embeddingModel;

    private Integer embeddingDimension;

    /** 抽样验证：第一条 chunk 实际生成的向量长度 */
    private Integer sampleVectorLength;

    private String chromaCollection;

    private Long chromaVectorCount;

    /** 回填示例：第一条 chunk 的 vectorId */
    private String sampleVectorId;
}
