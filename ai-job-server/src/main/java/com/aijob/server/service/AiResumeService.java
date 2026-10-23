package com.aijob.server.service;

import com.aijob.server.entity.ai.ResumeAnalysisResult;

public interface AiResumeService {

    ResumeAnalysisResult analyze(String content);
}
