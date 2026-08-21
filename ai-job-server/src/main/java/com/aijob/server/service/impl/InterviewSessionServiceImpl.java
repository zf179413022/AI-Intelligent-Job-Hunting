package com.aijob.server.service.impl;

import com.aijob.server.dto.InterviewSession;
import com.aijob.server.service.InterviewSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class InterviewSessionServiceImpl implements InterviewSessionService {

    public static final String KEY_PREFIX = "interview:session:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final long sessionTtlSeconds;

    public InterviewSessionServiceImpl(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            @Value("${interview.redis.session-ttl-seconds:7200}") long sessionTtlSeconds) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.sessionTtlSeconds = sessionTtlSeconds;
    }

    @Override
    public String key(Long interviewId) {
        return KEY_PREFIX + interviewId;
    }

    @Override
    public void save(InterviewSession session) {
        if (session == null || session.getInterviewId() == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(session);
            stringRedisTemplate.opsForValue().set(
                    key(session.getInterviewId()),
                    json,
                    Duration.ofSeconds(sessionTtlSeconds)
            );
        } catch (Exception e) {
            log.warn("Redis 保存面试会话失败，将仅依赖 MySQL。interviewId={}",
                    session.getInterviewId(), e);
        }
    }

    @Override
    public Optional<InterviewSession> get(Long interviewId) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key(interviewId));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, InterviewSession.class));
        } catch (Exception e) {
            log.warn("Redis 读取面试会话失败，将回退 MySQL。interviewId={}", interviewId, e);
            return Optional.empty();
        }
    }

    @Override
    public void delete(Long interviewId) {
        try {
            stringRedisTemplate.delete(key(interviewId));
        } catch (Exception e) {
            log.warn("Redis 删除面试会话失败。interviewId={}", interviewId, e);
        }
    }

    @Override
    public long getTtlSeconds(Long interviewId) {
        try {
            Long ttl = stringRedisTemplate.getExpire(key(interviewId), TimeUnit.SECONDS);
            return ttl == null ? -1 : ttl;
        } catch (Exception e) {
            log.warn("Redis 读取 TTL 失败。interviewId={}", interviewId, e);
            return -1;
        }
    }
}
