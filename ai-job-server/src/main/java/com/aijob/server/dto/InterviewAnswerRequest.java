package com.aijob.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InterviewAnswerRequest {

    @NotBlank(message = "回答内容不能为空")
    @Size(max = 5000, message = "回答内容过长")
    private String answer;
}
