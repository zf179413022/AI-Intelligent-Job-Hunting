package com.aijob.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JobMatchRequest {

    @NotNull(message = "resumeId 不能为空")
    private Long resumeId;

    @NotBlank(message = "岗位名称不能为空")
    @Size(min = 1, max = 100, message = "岗位名称长度需在 1~100 字")
    private String jobName;

    @Size(max = 100, message = "公司名称长度不能超过 100 字")
    private String companyName;

    @NotBlank(message = "岗位JD不能为空")
    @Size(min = 50, max = 10000, message = "岗位JD长度需在 50~10000 字")
    private String jobDescription;
}
