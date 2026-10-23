package com.aijob.server.entity.ai;

import lombok.Data;

@Data
public class InterviewAnswerAiResult {

    private String evaluation;

    private String nextQuestion;

    private Boolean shouldContinue;
}
