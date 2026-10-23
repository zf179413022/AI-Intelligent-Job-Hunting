package com.aijob.server.service;

import com.aijob.server.dto.InterviewAnswerReplay;
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

    /**
     * 抢占幂等键。抢到返回 empty，调用方继续生成；已有结果则返回上次回答。
     * Redis 不可用时返回 empty，本次不防重，避免面试被缓存故障挡住。
     */
    Optional<InterviewAnswerReplay> beginAnswer(Long interviewId, String requestId);

    void storeAnswer(Long interviewId, String requestId, InterviewAnswerReplay replay);

    void releaseAnswer(Long interviewId, String requestId);

    /**
     * 同一面试只允许一个进行中的回答。抢到返回 true。
     * Redis 不可用时返回 true，本次不能互斥，调用方仍要用轮次复查避免重复落库。
     */
    boolean acquireTurn(Long interviewId, String requestId, int round);

    void releaseTurn(Long interviewId, String requestId);
}
