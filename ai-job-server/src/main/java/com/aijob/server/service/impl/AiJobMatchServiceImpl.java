package com.aijob.server.service.impl;

import com.aijob.server.entity.ai.JobMatchAiResult;
import com.aijob.server.service.AiJobMatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiJobMatchServiceImpl implements AiJobMatchService {

    private final RestClient restClient;

    @Override
    public JobMatchAiResult match(
            String resumeContent,
            String jobName,
            String companyName,
            String jobDescription) {

        Map<String, Object> body = new HashMap<>();
        body.put("resumeContent", resumeContent);
        body.put("jobName", jobName);
        body.put("companyName", companyName == null ? "" : companyName);
        body.put("jobDescription", jobDescription);

        try {
            return restClient.post()
                    .uri("/api/ai/job-match")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JobMatchAiResult.class);
        } catch (RestClientResponseException e) {
            throw new RuntimeException(
                    "调用 Python AI 岗位匹配失败：" + e.getResponseBodyAsString(),
                    e
            );
        } catch (Exception e) {
            throw new RuntimeException("调用 Python AI 岗位匹配失败：" + e.getMessage(), e);
        }
    }
}
