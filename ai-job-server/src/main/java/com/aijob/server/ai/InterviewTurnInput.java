package com.aijob.server.ai;

/**
 * 一轮面试回答的输入。历史和简历摘要由调用方先整理好，图节点只消费这些字段。
 */
public record InterviewTurnInput(
        String position,
        String resume,
        String history,
        String question,
        String answer,
        int userRounds,
        Long userId,
        Long interviewId
) {
}
