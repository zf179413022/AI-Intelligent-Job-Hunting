package com.aijob.server.service.impl;

import com.aijob.server.entity.InterviewMessage;
import com.aijob.server.entity.ai.InterviewAnswerAiResult;
import com.aijob.server.entity.ai.InterviewReportAiResult;
import com.aijob.server.entity.ai.InterviewStartAiResult;
import com.aijob.server.service.AiInterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiInterviewServiceImpl implements AiInterviewService {

    private final RestClient restClient;

    @Override
    public InterviewStartAiResult start(String resumeContent, String position) {
        Map<String, Object> body = new HashMap<>();
        body.put("resumeContent", resumeContent);
        body.put("position", position);

        try {
            return restClient.post()
                    .uri("/api/ai/interview/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(InterviewStartAiResult.class);
        } catch (RestClientResponseException e) {
            throw new RuntimeException(
                    "调用 Python AI 面试开始失败：" + e.getResponseBodyAsString(),
                    e
            );
        } catch (Exception e) {
            throw new RuntimeException("调用 Python AI 面试开始失败：" + e.getMessage(), e);
        }
    }

    @Override
    public InterviewAnswerAiResult answer(
            String resumeContent,
            String position,
            List<InterviewMessage> history,
            String currentQuestion,
            String userAnswer) {

        Map<String, Object> body = new HashMap<>();
        body.put("resumeContent", resumeContent);
        body.put("position", position);
        body.put("history", toHistory(history));
        body.put("currentQuestion", currentQuestion);
        body.put("userAnswer", userAnswer);

        try {
            return restClient.post()
                    .uri("/api/ai/interview/answer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(InterviewAnswerAiResult.class);
        } catch (RestClientResponseException e) {
            throw new RuntimeException(
                    "调用 Python AI 面试回答失败：" + e.getResponseBodyAsString(),
                    e
            );
        } catch (Exception e) {
            throw new RuntimeException("调用 Python AI 面试回答失败：" + e.getMessage(), e);
        }
    }

    @Override
    public InterviewReportAiResult report(
            String resumeContent,
            String position,
            List<InterviewMessage> history) {

        Map<String, Object> body = new HashMap<>();
        body.put("resumeContent", resumeContent);
        body.put("position", position);
        body.put("history", toHistory(history));

        try {
            return restClient.post()
                    .uri("/api/ai/interview/report")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(InterviewReportAiResult.class);
        } catch (RestClientResponseException e) {
            throw new RuntimeException(
                    "调用 Python AI 面试报告失败：" + e.getResponseBodyAsString(),
                    e
            );
        } catch (Exception e) {
            throw new RuntimeException("调用 Python AI 面试报告失败：" + e.getMessage(), e);
        }
    }

    private List<Map<String, String>> toHistory(List<InterviewMessage> history) {
        List<Map<String, String>> list = new ArrayList<>();
        if (history == null) {
            return list;
        }
        for (InterviewMessage message : history) {
            Map<String, String> item = new HashMap<>();
            item.put("role", message.getRole());
            item.put("content", message.getContent());
            list.add(item);
        }
        return list;
    }
}
