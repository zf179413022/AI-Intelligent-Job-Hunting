package com.aijob.server.dto;

import lombok.Data;

/**
 * 同一客户端请求号已完成的回答。重复提交时直接返回，不再推进轮次。
 */
@Data
public class InterviewAnswerReplay {

    private String evaluation;

    private String nextQuestion;

    private Boolean shouldContinue;

    private Long aiMessageId;
}
