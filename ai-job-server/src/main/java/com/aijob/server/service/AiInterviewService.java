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
            String userAnswer
    );

    /**
     * 调用 Python SSE 流式点评接口，通过 handler 回调 delta / done / error。
     */
    void answerStream(
            String resumeContent,
            String position,
            List<InterviewMessage> history,
            String currentQuestion,
            String userAnswer,
            InterviewAnswerStreamHandler handler
    );

    InterviewReportAiResult report(
            String resumeContent,
            String position,
            List<InterviewMessage> history
    );
}
