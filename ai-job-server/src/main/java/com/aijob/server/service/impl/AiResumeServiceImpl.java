package com.aijob.server.service.impl;

import com.aijob.server.entity.ai.ResumeAnalysisResult;
import com.aijob.server.service.AiResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiResumeServiceImpl implements AiResumeService {

    private final RestClient restClient;

    @Override
    public ResumeAnalysisResult analyze(String content) {

        try {
            return restClient.post()
                    .uri("/api/ai/resume/analyze")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("content", content))
                    .retrieve()
                    .body(ResumeAnalysisResult.class);
        } catch (RestClientResponseException e) {
            throw new RuntimeException(
                    "调用 Python AI 服务失败：" + e.getResponseBodyAsString(),
                    e
            );
        } catch (Exception e) {
            throw new RuntimeException("调用 Python AI 服务失败：" + e.getMessage(), e);
        }
    }
}
