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

    InterviewReportAiResult report(
            String resumeContent,
            String position,
            List<InterviewMessage> history
    );
}
