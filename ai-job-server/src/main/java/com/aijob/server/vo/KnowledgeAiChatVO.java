package com.aijob.server.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeAiChatVO {

    private String prompt;

    private String answer;

    private String provider;
}
