package com.aijob.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InterviewCreateRequest {

    @NotNull(message = "resumeId 不能为空")
    private Long resumeId;

    @NotBlank(message = "面试岗位不能为空")
    @Size(max = 200, message = "面试岗位长度不能超过 200 字")
    private String position;
}
