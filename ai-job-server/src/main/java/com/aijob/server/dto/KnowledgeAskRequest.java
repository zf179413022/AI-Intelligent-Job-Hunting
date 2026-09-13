package com.aijob.server.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeAskRequest {

    @NotBlank(message = "question 不能为空")
    private String question;

    /**
     * Top-K，默认取配置 knowledge.rag.top-k。
     */
    @Min(1)
    @Max(20)
    private Integer topK;

    /**
     * 可选：限定在某一文档内检索（须属于当前用户）。
     */
    private Long documentId;
}
