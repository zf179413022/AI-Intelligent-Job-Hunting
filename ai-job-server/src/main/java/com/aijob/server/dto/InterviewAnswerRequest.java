package com.aijob.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InterviewAnswerRequest {

    @NotBlank(message = "回答内容不能为空")
    @Size(max = 5000, message = "回答内容过长")
    private String answer;

    /** 客户端生成的本轮请求号。相同请求号重复提交时返回上一次结果。 */
    @NotBlank(message = "requestId 不能为空")
    @Size(max = 64, message = "requestId 过长")
    private String requestId;
}
