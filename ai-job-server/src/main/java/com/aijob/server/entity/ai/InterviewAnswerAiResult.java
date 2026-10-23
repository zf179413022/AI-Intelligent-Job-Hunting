package com.aijob.server.entity.ai;

import lombok.Data;

@Data
public class InterviewAnswerAiResult {

    private String evaluation;

    private String nextQuestion;

    private Boolean shouldContinue;

    /** 服务端路由结果：probe / switch / conclude。不由模型 JSON 决定是否落库。 */
    private String routeAction;

    /** 知识库工具：NONE / SUCCESS / FAILED */
    private String toolStatus;
}
