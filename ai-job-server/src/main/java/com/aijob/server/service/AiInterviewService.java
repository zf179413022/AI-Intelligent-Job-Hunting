package com.aijob.server.service;

import com.aijob.server.entity.InterviewMessage;
import com.aijob.server.entity.ai.InterviewAnswerAiResult;
import com.aijob.server.entity.ai.InterviewReportAiResult;
import com.aijob.server.entity.ai.InterviewStartAiResult;

import java.util.List;

public interface AiInterviewService {

    InterviewStartAiResult start(String resumeContent, String position);

    InterviewAnswerAiResult answer(
            String resumeContent,
            String position,
            List<InterviewMessage> history,
            String currentQuestion,
            String userAnswer,
            Long userId,
            Long interviewId
    );

    /**
     * 流式点评。delta 为标记原文，done 为解析后的结果。
     * userId 只用于按需检索该用户自己的知识库。
     */
    void answerStream(
            String resumeContent,
            String position,
            List<InterviewMessage> history,
            String currentQuestion,
            String userAnswer,
            Long userId,
            Long interviewId,
            InterviewAnswerStreamHandler handler
    );

    InterviewReportAiResult report(
            String resumeContent,
            String position,
            List<InterviewMessage> history
    );
}
