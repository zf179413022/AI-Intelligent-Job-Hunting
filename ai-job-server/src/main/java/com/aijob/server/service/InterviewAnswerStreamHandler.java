package com.aijob.server.service;

import com.aijob.server.entity.ai.InterviewAnswerAiResult;

/**
 * 面试流式回调。delta 为模型原文片段，done 为解析后的点评和下一题。
 */
public interface InterviewAnswerStreamHandler {

    void onTool(String name, String status);

    void onRoute(String action);

    void onDelta(String content);

    void onDone(InterviewAnswerAiResult result);

    void onError(String message);
}
