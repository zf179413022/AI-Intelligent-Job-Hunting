package com.aijob.server.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewSessionServiceImplTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> values;

    private InterviewSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        service = new InterviewSessionServiceImpl(redis, new ObjectMapper(), 7200);
    }

    @Test
    void secondRequestDoesNotAcquireTheSameInterview() {
        when(values.setIfAbsent(eq("interview:turn:8"), eq("2:request-A"), any(Duration.class))).thenReturn(true);
        when(values.setIfAbsent(eq("interview:turn:8"), eq("2:request-B"), any(Duration.class))).thenReturn(false);
        when(values.get("interview:turn:8")).thenReturn("2:request-A");

        assertTrue(service.acquireTurn(8L, "request-A", 2));
        assertFalse(service.acquireTurn(8L, "request-B", 2));
    }

    @Test
    void releaseOnlyDeletesTheLockOwnedByTheSameRequest() {
        when(values.get("interview:turn:8")).thenReturn("2:request-A");

        service.releaseTurn(8L, "request-B");
        verify(redis, never()).delete(anyString());

        service.releaseTurn(8L, "request-A");
        verify(redis).delete("interview:turn:8");
    }

    @Test
    void redisOutageDoesNotBlockTheInterview() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("redis down"));

        assertTrue(service.acquireTurn(8L, "request-A", 2));
    }
}
