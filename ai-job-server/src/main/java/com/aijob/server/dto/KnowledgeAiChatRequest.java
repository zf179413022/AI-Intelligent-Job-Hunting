package com.aijob.server.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeAiChatRequest {

    @NotBlank(message = "prompt 不能为空")
    private String prompt;
}
