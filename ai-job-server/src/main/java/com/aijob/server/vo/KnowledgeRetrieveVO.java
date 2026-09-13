package com.aijob.server.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG 5.6.1：仅 Top-K 检索结果（不含 DeepSeek 生成）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeRetrieveVO {

    private String question;

    private Integer topK;

    private Integer hitCount;

    private List<KnowledgeSourceVO> sources;
}
