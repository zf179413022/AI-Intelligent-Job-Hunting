package com.aijob.server.service.impl;

import com.aijob.server.dto.InterviewAnswerReplay;
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

    private static final String IDEM_PREFIX = "interview:idem:";

    public static final String TURN_PREFIX = "interview:turn:";

    private static final long TURN_LOCK_SECONDS = 180;

    private static final String PENDING = "PENDING";

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

    @Override
    public Optional<InterviewAnswerReplay> beginAnswer(Long interviewId, String requestId) {
        String redisKey = idemKey(interviewId, requestId);
        try {
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                    redisKey, PENDING, Duration.ofSeconds(sessionTtlSeconds)
            );
            if (Boolean.TRUE.equals(locked)) {
                return Optional.empty();
            }
            String current = stringRedisTemplate.opsForValue().get(redisKey);
            if (current != null && current.startsWith("{")) {
                return Optional.of(objectMapper.readValue(current, InterviewAnswerReplay.class));
            }
            throw new RuntimeException("该轮回答正在处理，请稍后重试");
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("正在处理")) {
                throw e;
            }
            log.warn("幂等键不可用，本次回答不防重。interviewId={}", interviewId, e);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("幂等键不可用，本次回答不防重。interviewId={}", interviewId, e);
            return Optional.empty();
        }
    }

    @Override
    public void storeAnswer(Long interviewId, String requestId, InterviewAnswerReplay replay) {
        try {
            stringRedisTemplate.opsForValue().set(
                    idemKey(interviewId, requestId),
                    objectMapper.writeValueAsString(replay),
                    Duration.ofSeconds(sessionTtlSeconds)
            );
        } catch (Exception e) {
            log.warn("保存回答幂等结果失败。interviewId={}", interviewId, e);
        }
    }

    @Override
    public void releaseAnswer(Long interviewId, String requestId) {
        String redisKey = idemKey(interviewId, requestId);
        try {
            String current = stringRedisTemplate.opsForValue().get(redisKey);
            if (PENDING.equals(current)) {
                stringRedisTemplate.delete(redisKey);
            }
        } catch (Exception e) {
            log.warn("释放回答幂等键失败。interviewId={}", interviewId, e);
        }
    }

    @Override
    public boolean acquireTurn(Long interviewId, String requestId, int round) {
        String key = turnKey(interviewId);
        String value = turnValue(round, requestId);
        try {
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                    key, value, Duration.ofSeconds(TURN_LOCK_SECONDS)
            );
            if (Boolean.TRUE.equals(locked)) {
                return true;
            }
            String current = stringRedisTemplate.opsForValue().get(key);
            return value.equals(current);
        } catch (RuntimeException e) {
            log.warn("面试轮次互斥不可用，本次不阻止并发。interviewId={}, error={}",
                    interviewId, e.getClass().getSimpleName());
            return true;
        }
    }

    @Override
    public void releaseTurn(Long interviewId, String requestId) {
        String key = turnKey(interviewId);
        try {
            String current = stringRedisTemplate.opsForValue().get(key);
            if (ownsTurn(current, requestId)) {
                stringRedisTemplate.delete(key);
            }
        } catch (RuntimeException e) {
            log.warn("释放面试轮次互斥失败。interviewId={}, error={}",
                    interviewId, e.getClass().getSimpleName());
        }
    }

    private static String idemKey(Long interviewId, String requestId) {
        return IDEM_PREFIX + interviewId + ":" + requestId;
    }

    private static String turnKey(Long interviewId) {
        return TURN_PREFIX + interviewId;
    }

    static String turnValue(int round, String requestId) {
        return round + ":" + requestId;
    }

    static boolean ownsTurn(String current, String requestId) {
        if (current == null || requestId == null) {
            return false;
        }
        int split = current.indexOf(':');
        if (split < 0 || split == current.length() - 1) {
            return false;
        }
        return requestId.equals(current.substring(split + 1));
    }
}
