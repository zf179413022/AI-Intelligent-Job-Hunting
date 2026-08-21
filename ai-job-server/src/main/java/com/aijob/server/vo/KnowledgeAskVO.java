package com.aijob.server.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeAskVO {

    private String question;

    private String answer;

    private List<KnowledgeSourceVO> sources;

    private Integer topK;

    private Integer hitCount;

    private String provider;

    private Long qaId;
}
