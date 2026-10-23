package com.aijob.server.service.impl;

import com.aijob.server.entity.InterviewMessage;
import com.aijob.server.entity.ai.InterviewAnswerAiResult;
import com.aijob.server.entity.ai.InterviewReportAiResult;
import com.aijob.server.entity.ai.InterviewStartAiResult;
import com.aijob.server.service.AiInterviewService;
import com.aijob.server.service.InterviewAnswerStreamHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiInterviewServiceImpl implements AiInterviewService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

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

        Map<String, Object> body = buildAnswerBody(
                resumeContent, position, history, currentQuestion, userAnswer
        );

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
    public void answerStream(
            String resumeContent,
            String position,
            List<InterviewMessage> history,
            String currentQuestion,
            String userAnswer,
            InterviewAnswerStreamHandler handler) {

        Map<String, Object> body = buildAnswerBody(
                resumeContent, position, history, currentQuestion, userAnswer
        );

        try {
            restClient.post()
                    .uri("/api/ai/interview/answer/stream")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .body(body)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isError()) {
                            String errBody = "";
                            try (InputStream err = response.getBody()) {
                                if (err != null) {
                                    errBody = new String(err.readAllBytes(), StandardCharsets.UTF_8);
                                }
                            }
                            handler.onError("调用 Python AI 流式面试失败："
                                    + response.getStatusCode().value() + " " + errBody);
                            return null;
                        }
                        try (InputStream inputStream = response.getBody();
                             BufferedReader reader = new BufferedReader(
                                     new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                            parseSse(reader, handler);
                        } catch (Exception e) {
                            handler.onError("解析 Python SSE 失败：" + e.getMessage());
                        }
                        return null;
                    });
        } catch (Exception e) {
            handler.onError("调用 Python AI 流式面试失败：" + e.getMessage());
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

    private void parseSse(BufferedReader reader, InterviewAnswerStreamHandler handler)
            throws Exception {
        String event = "message";
        StringBuilder data = new StringBuilder();
        String line;
        boolean finished = false;

        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                if (data.length() == 0) {
                    event = "message";
                    continue;
                }
                String payload = data.toString();
                data.setLength(0);
                String currentEvent = event;
                event = "message";

                if ("delta".equals(currentEvent)) {
                    JsonNode node = objectMapper.readTree(payload);
                    String content = node.path("content").asText("");
                    if (!content.isEmpty()) {
                        handler.onDelta(content);
                    }
                } else if ("done".equals(currentEvent)) {
                    InterviewAnswerAiResult result =
                            objectMapper.readValue(payload, InterviewAnswerAiResult.class);
                    handler.onDone(result);
                    finished = true;
                } else if ("error".equals(currentEvent)) {
                    JsonNode node = objectMapper.readTree(payload);
                    String message = node.path("message").asText("AI流式面试失败");
                    handler.onError(message);
                    finished = true;
                }
                continue;
            }

            if (line.startsWith("event:")) {
                event = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                if (data.length() > 0) {
                    data.append('\n');
                }
                data.append(line.substring(5).trim());
            }
        }

        if (!finished && data.length() > 0) {
            // 末尾无空行时的兜底
            if ("done".equals(event)) {
                handler.onDone(objectMapper.readValue(data.toString(), InterviewAnswerAiResult.class));
            } else if ("error".equals(event)) {
                JsonNode node = objectMapper.readTree(data.toString());
                handler.onError(node.path("message").asText("AI流式面试失败"));
            }
        }
    }

    private Map<String, Object> buildAnswerBody(
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
        return body;
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
