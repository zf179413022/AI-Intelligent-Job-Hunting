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

    private Double score;
}
