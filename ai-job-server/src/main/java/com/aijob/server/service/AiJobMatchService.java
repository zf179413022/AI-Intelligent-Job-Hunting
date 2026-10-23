package com.aijob.server.service;

import com.aijob.server.entity.ai.JobMatchAiResult;

public interface AiJobMatchService {

    JobMatchAiResult match(String resumeContent, String jobName, String companyName, String jobDescription);
}
