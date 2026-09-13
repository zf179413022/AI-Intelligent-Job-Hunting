package com.aijob.server.service;

import com.aijob.server.entity.ai.InterviewAnswerAiResult;

/**
 * Python AI 面试流式回调。
 */
public interface InterviewAnswerStreamHandler {

    void onDelta(String content);

    void onDone(InterviewAnswerAiResult result);

    void onError(String message);
}
