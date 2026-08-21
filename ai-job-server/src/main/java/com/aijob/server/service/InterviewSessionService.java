package com.aijob.server.service;

import com.aijob.server.dto.InterviewSession;

import java.util.Optional;

public interface InterviewSessionService {

    String key(Long interviewId);

    void save(InterviewSession session);

    Optional<InterviewSession> get(Long interviewId);

    void delete(Long interviewId);

    /**
     * @return TTL 秒数；key 不存在或 Redis 不可用返回 -1
     */
    long getTtlSeconds(Long interviewId);
}
