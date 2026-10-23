package com.aijob.server.service.impl;

import com.aijob.server.dto.InterviewAnswerReplay;
import com.aijob.server.dto.InterviewSession;
import com.aijob.server.entity.Interview;
import com.aijob.server.entity.InterviewMessage;
import com.aijob.server.entity.Resume;
import com.aijob.server.entity.ai.InterviewAnswerAiResult;
import com.aijob.server.mapper.InterviewMapper;
import com.aijob.server.mapper.InterviewReportMapper;
import com.aijob.server.service.AiInterviewService;
import com.aijob.server.service.InterviewAnswerStreamHandler;
import com.aijob.server.service.InterviewMessageService;
import com.aijob.server.service.InterviewSessionService;
import com.aijob.server.service.ResumeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewAnswerStreamDisconnectTest {

    @Mock
    private InterviewMapper interviewMapper;
    @Mock
    private InterviewReportMapper interviewReportMapper;
    @Mock
    private InterviewMessageService interviewMessageService;
    @Mock
    private InterviewSessionService interviewSessionService;
    @Mock
    private ResumeService resumeService;
    @Mock
    private AiInterviewService aiInterviewService;

    private InterviewServiceImpl service;
    private final AtomicReference<InterviewAnswerReplay> stored = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        service = new InterviewServiceImpl(
                interviewMapper,
                interviewReportMapper,
                interviewMessageService,
                interviewSessionService,
                resumeService,
                aiInterviewService,
                new ObjectMapper()
        );
        Interview interview = new Interview();
        interview.setId(1L);
        interview.setUserId(7L);
        interview.setResumeId(3L);
        interview.setStatus("RUNNING");
        interview.setPosition("Java 后端");
        when(interviewMapper.selectById(1L)).thenReturn(interview);

        Resume resume = new Resume();
        resume.setId(3L);
        resume.setUserId(7L);
        resume.setContent("熟悉 Java");
        when(resumeService.getById(3L)).thenReturn(resume);

        InterviewSession session = new InterviewSession();
        session.setInterviewId(1L);
        session.setUserId(7L);
        session.setResumeId(3L);
        session.setRound(0);
        session.setStage("opening");
        session.setResumeContent("熟悉 Java");
        session.getMessages().add(new InterviewSession.SessionMessage("AI", "介绍一个项目"));
        when(interviewSessionService.get(1L)).thenReturn(Optional.of(session));
        when(interviewSessionService.acquireTurn(1L, "request-A", 0)).thenReturn(true);
        when(interviewSessionService.beginAnswer(eq(1L), eq("request-A"))).thenAnswer(invocation -> {
            InterviewAnswerReplay saved = stored.get();
            return saved == null ? Optional.empty() : Optional.of(saved);
        });
        when(interviewMessageService.listByInterviewId(1L)).thenReturn(java.util.List.of());
        when(interviewMessageService.save(anyLong(), anyString(), anyString())).thenAnswer(invocation -> message(
                8L, invocation.getArgument(1), invocation.getArgument(2)
        ));
        when(interviewMessageService.save(anyLong(), anyString(), anyString(), any(), any())).thenAnswer(invocation -> message(
                9L, invocation.getArgument(1), invocation.getArgument(2)
        ));
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(2));
            return null;
        }).when(interviewSessionService).storeAnswer(eq(1L), eq("request-A"), any());
        doAnswer(invocation -> {
            InterviewAnswerStreamHandler handler = invocation.getArgument(7);
            handler.onTool("searchKnowledgeBase", "START");
            handler.onRoute("probe");
            handler.onDelta("片段");
            InterviewAnswerAiResult result = new InterviewAnswerAiResult();
            result.setEvaluation("不错");
            result.setNextQuestion("下一题");
            result.setShouldContinue(true);
            result.setRouteAction("probe");
            result.setToolStatus("SUCCESS");
            handler.onDone(result);
            return null;
        }).when(aiInterviewService).answerStream(
                anyString(), anyString(), any(), anyString(), anyString(), any(), any(), any()
        );
    }

    @Test
    void keepsPersistenceWhenEveryEventIsDelivered() throws IOException {
        SseEmitter emitter = emitterThatFailsOn(0);

        service.answerStream(1L, 7L, "我的回答", "request-A", emitter);

        verifyPersistedOnce();
        verify(emitter).complete();
    }

    @Test
    void keepsPersistenceWhenToolSendFails() {
        service.answerStream(1L, 7L, "我的回答", "request-A", emitterThatFailsOn(1));

        verifyPersistedOnce();
    }

    @Test
    void keepsPersistenceWhenRouteSendFails() {
        service.answerStream(1L, 7L, "我的回答", "request-A", emitterThatFailsOn(2));

        verifyPersistedOnce();
    }

    @Test
    void keepsPersistenceWhenDeltaSendFails() {
        service.answerStream(1L, 7L, "我的回答", "request-A", emitterThatFailsOn(3));

        verifyPersistedOnce();
    }

    @Test
    void keepsPersistenceWhenDoneSendFails() {
        service.answerStream(1L, 7L, "我的回答", "request-A", emitterThatFailsOn(4));

        verifyPersistedOnce();
    }

    @Test
    void doesNotRunTheAgentAgainAfterADisconnectedRequestWasSaved() {
        service.answerStream(1L, 7L, "我的回答", "request-A", emitterThatFailsOn(1));
        when(interviewMessageService.findById(1L, 9L)).thenReturn(message(9L, "AI", "【点评】不错"));

        service.answerStream(1L, 7L, "我的回答", "request-A", emitterThatFailsOn(0));

        verify(aiInterviewService, times(1)).answerStream(
                anyString(), anyString(), any(), anyString(), anyString(), any(), any(), any()
        );
        verifyPersistedOnce();
    }

    private void verifyPersistedOnce() {
        verify(interviewMessageService).save(1L, "USER", "我的回答");
        verify(interviewMessageService).save(eq(1L), eq("AI"), anyString(), eq("probe"), eq("SUCCESS"));
        verify(interviewSessionService).save(any(InterviewSession.class));
        verify(interviewSessionService).storeAnswer(eq(1L), eq("request-A"), any());
        verify(interviewSessionService, never()).releaseAnswer(anyLong(), anyString());
    }

    private static SseEmitter emitterThatFailsOn(int attempt) {
        SseEmitter emitter = mock(SseEmitter.class);
        AtomicInteger sends = new AtomicInteger();
        try {
            doAnswer(invocation -> {
                if (attempt > 0 && sends.incrementAndGet() == attempt) {
                    throw new IOException("broken pipe");
                }
                return null;
            }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return emitter;
    }

    private static InterviewMessage message(Long id, String role, String content) {
        InterviewMessage message = new InterviewMessage();
        message.setId(id);
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
