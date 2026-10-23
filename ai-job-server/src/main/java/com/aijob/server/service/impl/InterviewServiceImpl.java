package com.aijob.server.service.impl;

import com.aijob.server.exception.ForbiddenException;

import com.aijob.server.dto.InterviewAnswerReplay;
import com.aijob.server.dto.InterviewSession;
import com.aijob.server.entity.Interview;
import com.aijob.server.entity.InterviewMessage;
import com.aijob.server.entity.InterviewReport;
import com.aijob.server.entity.Resume;
import com.aijob.server.entity.ai.InterviewAnswerAiResult;
import com.aijob.server.entity.ai.InterviewReportAiResult;
import com.aijob.server.entity.ai.InterviewStartAiResult;
import com.aijob.server.mapper.InterviewMapper;
import com.aijob.server.mapper.InterviewReportMapper;
import com.aijob.server.service.AiInterviewService;
import com.aijob.server.service.InterviewAnswerStreamHandler;
import com.aijob.server.service.InterviewMessageService;
import com.aijob.server.service.InterviewService;
import com.aijob.server.service.InterviewSessionService;
import com.aijob.server.service.ResumeService;
import com.aijob.server.vo.InterviewAnswerVO;
import com.aijob.server.vo.InterviewStartVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {

    private final InterviewMapper interviewMapper;
    private final InterviewReportMapper interviewReportMapper;
    private final InterviewMessageService interviewMessageService;
    private final InterviewSessionService interviewSessionService;
    private final ResumeService resumeService;
    private final AiInterviewService aiInterviewService;
    private final ObjectMapper objectMapper;

    @Override
    public Interview create(Long userId, Long resumeId, String position) {

        if (position == null || position.isBlank()) {
            throw new RuntimeException("面试岗位不能为空");
        }

        Resume resume = resumeService.getById(resumeId);

        if (resume == null) {
            throw new RuntimeException("简历不存在");
        }

        if (!resume.getUserId().equals(userId)) {
            throw new ForbiddenException("无权使用该简历进行面试");
        }

        if (resume.getContent() == null || resume.getContent().isBlank()) {
            throw new RuntimeException("请先解析简历");
        }

        Interview interview = new Interview();
        interview.setUserId(userId);
        interview.setResumeId(resumeId);
        interview.setPosition(position.trim());
        interview.setStatus("WAITING");
        interview.setScore(null);
        interview.setStartTime(null);
        interview.setEndTime(null);

        interviewMapper.insert(interview);

        return interview;
    }

    @Override
    public List<Interview> listByUserId(Long userId) {
        return interviewMapper.selectList(
                new LambdaQueryWrapper<Interview>()
                        .eq(Interview::getUserId, userId)
                        .orderByDesc(Interview::getCreatedAt)
        );
    }

    @Override
    public Interview getById(Long id, Long userId) {
        Interview interview = interviewMapper.selectById(id);

        if (interview == null) {
            throw new RuntimeException("面试不存在");
        }

        if (!interview.getUserId().equals(userId)) {
            throw new ForbiddenException("无权访问该面试");
        }

        return interview;
    }

    @Override
    public List<InterviewMessage> listMessages(Long id, Long userId) {
        getById(id, userId);
        return interviewMessageService.listByInterviewId(id);
    }

    @Override
    public InterviewReport getReport(Long id, Long userId) {
        getById(id, userId);

        InterviewReport report = interviewReportMapper.selectOne(
                new LambdaQueryWrapper<InterviewReport>()
                        .eq(InterviewReport::getInterviewId, id)
        );

        if (report == null) {
            throw new RuntimeException("该面试暂无报告，请先结束面试");
        }

        return report;
    }

    @Override
    @Transactional
    public InterviewStartVO start(Long id, Long userId) {
        Interview interview = getById(id, userId);

        if (!"WAITING".equals(interview.getStatus())) {
            throw new RuntimeException("当前面试无法开始");
        }

        Resume resume = requireParsedResume(interview);

        InterviewStartAiResult aiResult =
                aiInterviewService.start(resume.getContent(), interview.getPosition());

        if (aiResult == null
                || aiResult.getQuestion() == null
                || aiResult.getQuestion().isBlank()) {
            throw new RuntimeException("AI未返回有效面试题");
        }

        interview.setStatus("RUNNING");
        interview.setStartTime(LocalDateTime.now());
        interviewMapper.updateById(interview);

        String question = aiResult.getQuestion().trim();
        InterviewMessage questionMessage = interviewMessageService.save(
                interview.getId(),
                "AI",
                question
        );

        InterviewSession session = newSession(interview, resume);
        session.setStatus("RUNNING");
        session.setRound(0);
        session.setStage("opening");
        session.setCurrentQuestion(question);
        session.setLastToolStatus("NONE");
        session.getMessages().add(new InterviewSession.SessionMessage("AI", question));
        interviewSessionService.save(session);

        return new InterviewStartVO(interviewMapper.selectById(id), questionMessage);
    }

    @Override
    @Transactional
    public InterviewAnswerVO answer(Long id, Long userId, String answer, String requestId) {
        Interview interview = getById(id, userId);

        if (!"RUNNING".equals(interview.getStatus())) {
            throw new RuntimeException("当前面试未在进行中，无法回答");
        }

        if (answer == null || answer.isBlank()) {
            throw new RuntimeException("回答内容不能为空");
        }
        requireRequestId(requestId);

        Optional<InterviewAnswerReplay> replay = interviewSessionService.beginAnswer(id, requestId);
        if (replay.isPresent()) {
            return replayVo(id, userId, replay.get());
        }

        boolean turnHeld = false;
        try {
            Resume resume = requireParsedResume(interview);
            HistoryBundle historyBundle = loadHistoryForAi(interview, resume);
            List<InterviewMessage> history = historyBundle.messages();

            String currentQuestion = findLatestAiQuestion(history);
            if (currentQuestion == null) {
                throw new RuntimeException("未找到当前面试题，请重新开始面试");
            }
            int expectedRound = countUserMessages(history);
            if (!interviewSessionService.acquireTurn(id, requestId, expectedRound)) {
                throw new RuntimeException("当前面试轮次正在处理，请稍后重试");
            }
            turnHeld = true;

            InterviewAnswerAiResult aiResult = aiInterviewService.answer(
                    historyBundle.resumeContent(),
                    interview.getPosition(),
                    history,
                    currentQuestion,
                    answer.trim(),
                    userId,
                    interview.getId()
            );

            if (aiResult == null) {
                throw new RuntimeException("AI面试回答分析失败");
            }

            PersistedAnswer persisted = persistAnswerResult(
                    interview, resume, historyBundle, history, answer.trim(), aiResult, expectedRound
            );
            InterviewAnswerVO vo = new InterviewAnswerVO(
                    interviewMapper.selectById(id),
                    persisted.evaluation(),
                    persisted.aiMessage(),
                    persisted.shouldContinue()
            );
            interviewSessionService.storeAnswer(id, requestId, toReplay(persisted, aiResult));
            return vo;
        } catch (RuntimeException e) {
            interviewSessionService.releaseAnswer(id, requestId);
            throw e;
        } finally {
            if (turnHeld) {
                interviewSessionService.releaseTurn(id, requestId);
            }
        }
    }

    @Override
    public void answerStream(Long id, Long userId, String answer, String requestId, SseEmitter emitter) {
        try {
            Interview interview = getById(id, userId);

            if (!"RUNNING".equals(interview.getStatus())) {
                sendSseError(emitter, "当前面试未在进行中，无法回答");
                return;
            }

            if (answer == null || answer.isBlank()) {
                sendSseError(emitter, "回答内容不能为空");
                return;
            }
            requireRequestId(requestId);

            Optional<InterviewAnswerReplay> replay = interviewSessionService.beginAnswer(id, requestId);
            if (replay.isPresent()) {
                sendAnswerDone(emitter, new AtomicBoolean(false), id, requestId,
                        replayVo(id, userId, replay.get()), replay.get().getNextQuestion());
                return;
            }

            Resume resume = requireParsedResume(interview);
            HistoryBundle historyBundle = loadHistoryForAi(interview, resume);
            List<InterviewMessage> history = historyBundle.messages();

            String currentQuestion = findLatestAiQuestion(history);
            if (currentQuestion == null) {
                interviewSessionService.releaseAnswer(id, requestId);
                sendSseError(emitter, "未找到当前面试题，请重新开始面试");
                return;
            }
            int expectedRound = countUserMessages(history);
            if (!interviewSessionService.acquireTurn(id, requestId, expectedRound)) {
                interviewSessionService.releaseAnswer(id, requestId);
                sendSseError(emitter, "当前面试轮次正在处理，请稍后重试");
                return;
            }

            final String trimmedAnswer = answer.trim();
            final AtomicBoolean sseClosed = new AtomicBoolean(false);
            try {
            aiInterviewService.answerStream(
                    historyBundle.resumeContent(),
                    interview.getPosition(),
                    history,
                    currentQuestion,
                    trimmedAnswer,
                    userId,
                    interview.getId(),
                    new InterviewAnswerStreamHandler() {
                        @Override
                        public void onTool(String name, String status) {
                            Map<String, String> payload = new LinkedHashMap<>();
                            payload.put("name", name);
                            payload.put("status", status);
                            safeSend(emitter, sseClosed, id, requestId, "tool",
                                    SseEmitter.event().name("tool").data(payload));
                        }

                        @Override
                        public void onRoute(String action) {
                            safeSend(emitter, sseClosed, id, requestId, "route",
                                    SseEmitter.event().name("route")
                                            .data(Map.of("action", action == null ? "" : action)));
                        }

                        @Override
                        public void onDelta(String content) {
                            safeSend(emitter, sseClosed, id, requestId, "delta",
                                    SseEmitter.event().name("delta").data(Map.of("content", content)));
                        }

                        @Override
                        public void onDone(InterviewAnswerAiResult aiResult) {
                            try {
                                if (aiResult == null) {
                                    interviewSessionService.releaseAnswer(id, requestId);
                                    safeSend(emitter, sseClosed, id, requestId, "error",
                                            SseEmitter.event().name("error")
                                                    .data(Map.of("message", "AI面试回答分析失败")));
                                    return;
                                }
                                PersistedAnswer persisted = persistAnswerResult(
                                        interview,
                                        resume,
                                        historyBundle,
                                        history,
                                        trimmedAnswer,
                                        aiResult,
                                        expectedRound
                                );
                                interviewSessionService.storeAnswer(id, requestId, toReplay(persisted, aiResult));
                                sendAnswerDone(emitter, sseClosed, id, requestId, new InterviewAnswerVO(
                                        interviewMapper.selectById(id),
                                        persisted.evaluation(),
                                        persisted.aiMessage(),
                                        persisted.shouldContinue()
                                ), aiResult.getNextQuestion());
                            } catch (Exception e) {
                                interviewSessionService.releaseAnswer(id, requestId);
                                safeSend(emitter, sseClosed, id, requestId, "error",
                                        SseEmitter.event().name("error").data(Map.of(
                                                "message",
                                                e.getMessage() == null ? "流式面试落库失败" : e.getMessage()
                                        )));
                            }
                        }

                        @Override
                        public void onError(String message) {
                            interviewSessionService.releaseAnswer(id, requestId);
                            safeSend(emitter, sseClosed, id, requestId, "error",
                                    SseEmitter.event().name("error").data(Map.of(
                                            "message",
                                            message == null ? "AI流式面试失败" : message
                                    )));
                        }
                    }
            );
            } finally {
                interviewSessionService.releaseTurn(id, requestId);
            }
        } catch (Exception e) {
            if (e.getMessage() == null || !e.getMessage().contains("正在处理")) {
                interviewSessionService.releaseAnswer(id, requestId);
            }
            sendSseError(emitter, e.getMessage() == null ? "流式面试失败" : e.getMessage());
        }
    }

    /**
     * MySQL 只在 AI 结果完整后保存一次 USER + AI 消息，并刷新 Redis TTL。
     */
    private PersistedAnswer persistAnswerResult(
            Interview interview,
            Resume resume,
            HistoryBundle historyBundle,
            List<InterviewMessage> history,
            String userAnswer,
            InterviewAnswerAiResult aiResult,
            int expectedRound) {

        requireRoundNotAdvanced(expectedRound, countUserMessages(
                interviewMessageService.listByInterviewId(interview.getId())));

        InterviewMessage userMessage =
                interviewMessageService.save(interview.getId(), "USER", userAnswer);

        String evaluation = aiResult.getEvaluation() == null
                ? ""
                : aiResult.getEvaluation().trim();
        boolean shouldContinue = Boolean.TRUE.equals(aiResult.getShouldContinue());
        String nextQuestion = aiResult.getNextQuestion() == null
                ? ""
                : aiResult.getNextQuestion().trim();

        InterviewMessage nextQuestionMessage = null;
        String aiPersistContent = null;
        if (shouldContinue && !nextQuestion.isBlank()) {
            aiPersistContent = evaluation.isBlank()
                    ? nextQuestion
                    : "【点评】" + evaluation + "\n\n【追问】" + nextQuestion;
            nextQuestionMessage = interviewMessageService.save(
                    interview.getId(),
                    "AI",
                    aiPersistContent,
                    aiResult.getRouteAction(),
                    aiResult.getToolStatus()
            );
        } else if (!evaluation.isBlank()) {
            aiPersistContent = "【点评】" + evaluation + "\n\n本轮面试问题已足够，可以结束并生成报告。";
            nextQuestionMessage = interviewMessageService.save(
                    interview.getId(),
                    "AI",
                    aiPersistContent,
                    aiResult.getRouteAction(),
                    aiResult.getToolStatus()
            );
            shouldContinue = false;
        }

        InterviewSession session = historyBundle.session().orElseGet(
                () -> newSession(interview, resume)
        );
        session.setStatus("RUNNING");
        session.setRound(expectedRound + 1);
        session.setStage(stageOf(aiResult.getRouteAction()));
        session.setLastRouteAction(aiResult.getRouteAction());
        session.setLastToolStatus(aiResult.getToolStatus() == null ? "NONE" : aiResult.getToolStatus());
        if (shouldContinue && !nextQuestion.isBlank()) {
            session.setCurrentQuestion(nextQuestion);
        } else {
            String asked = findLatestAiQuestion(history);
            if (asked != null) {
                session.setCurrentQuestion(asked);
            }
        }
        session.setResumeContent(historyBundle.resumeContent());
        if (session.getMessages() == null || session.getMessages().isEmpty()) {
            session.setMessages(toSessionMessages(history));
        }
        session.getMessages().add(
                new InterviewSession.SessionMessage("USER", userMessage.getContent())
        );
        if (aiPersistContent != null) {
            session.getMessages().add(
                    new InterviewSession.SessionMessage("AI", aiPersistContent)
            );
        }
        interviewSessionService.save(session);

        return new PersistedAnswer(evaluation, nextQuestionMessage, shouldContinue);
    }

    private InterviewAnswerVO replayVo(Long interviewId, Long userId, InterviewAnswerReplay replay) {
        Interview interview = getById(interviewId, userId);
        InterviewMessage message = interviewMessageService.findById(interviewId, replay.getAiMessageId());
        return new InterviewAnswerVO(
                interview,
                replay.getEvaluation(),
                message,
                replay.getShouldContinue()
        );
    }

    private void sendAnswerDone(
            SseEmitter emitter,
            AtomicBoolean sseClosed,
            Long interviewId,
            String requestId,
            InterviewAnswerVO vo,
            String nextQuestion) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evaluation", vo.getEvaluation() == null ? "" : vo.getEvaluation());
        payload.put("nextQuestion", nextQuestion == null ? "" : nextQuestion.trim());
        payload.put("shouldContinue", Boolean.TRUE.equals(vo.getShouldContinue()));
        payload.put("message", vo.getNextQuestionMessage());
        payload.put("interview", vo.getInterview());
        safeSend(emitter, sseClosed, interviewId, requestId, "done",
                SseEmitter.event().name("done").data(payload));
        safeComplete(emitter, sseClosed);
    }

    /**
     * SSE 只负责通知。发送失败后不再推，也不把异常交给本轮落库。
     */
    private void safeSend(
            SseEmitter emitter,
            AtomicBoolean sseClosed,
            Long interviewId,
            String requestId,
            String eventType,
            SseEmitter.SseEventBuilder event) {
        if (sseClosed.get()) {
            return;
        }
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            sseClosed.set(true);
            log.debug("SSE 连接已断开，停止推送。interviewId={}, requestId={}, eventType={}",
                    interviewId, requestId, eventType);
        }
    }

    private void safeComplete(SseEmitter emitter, AtomicBoolean sseClosed) {
        if (sseClosed.get()) {
            return;
        }
        try {
            emitter.complete();
        } catch (IllegalStateException e) {
            sseClosed.set(true);
        }
    }

    private static InterviewAnswerReplay toReplay(PersistedAnswer persisted, InterviewAnswerAiResult aiResult) {
        InterviewAnswerReplay replay = new InterviewAnswerReplay();
        replay.setEvaluation(persisted.evaluation());
        replay.setNextQuestion(aiResult.getNextQuestion() == null ? "" : aiResult.getNextQuestion().trim());
        replay.setShouldContinue(persisted.shouldContinue());
        replay.setAiMessageId(persisted.aiMessage() == null ? null : persisted.aiMessage().getId());
        return replay;
    }

    private static void requireRequestId(String requestId) {
        if (requestId == null || requestId.isBlank() || requestId.length() > 64) {
            throw new RuntimeException("requestId 不能为空");
        }
    }

    private void sendSseError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(Map.of("message", message == null ? "未知错误" : message)));
            emitter.complete();
        } catch (Exception ignored) {
            emitter.completeWithError(new RuntimeException(message));
        }
    }

    @Override
    @Transactional
    public InterviewReport finish(Long id, Long userId) {
        Interview interview = getById(id, userId);

        if (!"RUNNING".equals(interview.getStatus())) {
            throw new RuntimeException("当前面试无法结束");
        }

        Resume resume = requireParsedResume(interview);
        HistoryBundle historyBundle = loadHistoryForAi(interview, resume);
        List<InterviewMessage> history = historyBundle.messages();

        if (history.isEmpty()) {
            throw new RuntimeException("尚无面试对话，无法生成报告");
        }

        InterviewReportAiResult aiResult = aiInterviewService.report(
                historyBundle.resumeContent(),
                interview.getPosition(),
                history
        );

        if (aiResult == null) {
            throw new RuntimeException("AI未返回有效面试报告");
        }
        requireScore(aiResult.getTotalScore());
        requireScore(aiResult.getJavaScore());
        requireScore(aiResult.getMysqlScore());
        requireScore(aiResult.getRedisScore());
        requireScore(aiResult.getSpringScore());
        if (aiResult.getSummary() == null || aiResult.getSummary().isBlank()) {
            throw new RuntimeException("AI返回的面试报告不合法");
        }

        Integer totalScore = aiResult.getTotalScore();

        InterviewReport existing = interviewReportMapper.selectOne(
                new LambdaQueryWrapper<InterviewReport>()
                        .eq(InterviewReport::getInterviewId, interview.getId())
        );

        InterviewReport report = existing == null ? new InterviewReport() : existing;
        report.setInterviewId(interview.getId());
        report.setTotalScore(totalScore);
        report.setJavaScore(aiResult.getJavaScore());
        report.setMysqlScore(aiResult.getMysqlScore());
        report.setRedisScore(aiResult.getRedisScore());
        report.setSpringScore(aiResult.getSpringScore());
        report.setWeakPoints(toJsonArray(aiResult.getWeakPoints()));
        report.setSuggestions(toJsonArray(aiResult.getSuggestions()));
        report.setSummary(aiResult.getSummary());

        if (existing == null) {
            interviewReportMapper.insert(report);
        } else {
            interviewReportMapper.updateById(report);
        }

        interview.setStatus("COMPLETED");
        interview.setEndTime(LocalDateTime.now());
        interview.setScore(totalScore);
        interviewMapper.updateById(interview);

        // 结束后面试临时会话从 Redis 删除
        interviewSessionService.delete(interview.getId());

        return interviewReportMapper.selectOne(
                new LambdaQueryWrapper<InterviewReport>()
                        .eq(InterviewReport::getInterviewId, interview.getId())
        );
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        Interview interview = getById(id, userId);

        interviewMessageService.deleteByInterviewId(interview.getId());

        interviewReportMapper.delete(
                new LambdaQueryWrapper<InterviewReport>()
                        .eq(InterviewReport::getInterviewId, interview.getId())
        );

        interviewMapper.deleteById(interview.getId());
        interviewSessionService.delete(interview.getId());
    }

    /**
     * 优先 Redis 会话上下文；不可用或未命中时回退 MySQL，并可回填 Redis。
     */
    private HistoryBundle loadHistoryForAi(Interview interview, Resume resume) {
        Optional<InterviewSession> cached = interviewSessionService.get(interview.getId());
        if (cached.isPresent()
                && cached.get().getMessages() != null
                && !cached.get().getMessages().isEmpty()) {
            InterviewSession session = cached.get();
            String resumeContent = session.getResumeContent();
            if (resumeContent == null || resumeContent.isBlank()) {
                resumeContent = resume.getContent();
                session.setResumeContent(resumeContent);
            }
            if (session.getRound() == null || session.getStage() == null) {
                recoverState(session, interviewMessageService.listByInterviewId(interview.getId()));
                interviewSessionService.save(session);
            }
            log.debug("面试上下文来自 Redis, interviewId={}", interview.getId());
            return new HistoryBundle(toInterviewMessages(session), resumeContent, cached);
        }

        log.debug("面试上下文回退 MySQL, interviewId={}", interview.getId());
        List<InterviewMessage> fromDb =
                interviewMessageService.listByInterviewId(interview.getId());

        InterviewSession session = newSession(interview, resume);
        session.setStatus(interview.getStatus());
        session.setMessages(toSessionMessages(fromDb));
        recoverState(session, fromDb);
        interviewSessionService.save(session);

        return new HistoryBundle(fromDb, resume.getContent(), Optional.of(session));
    }

    private InterviewSession newSession(Interview interview, Resume resume) {
        InterviewSession session = new InterviewSession();
        session.setInterviewId(interview.getId());
        session.setUserId(interview.getUserId());
        session.setResumeId(interview.getResumeId());
        session.setPosition(interview.getPosition());
        session.setResumeContent(resume.getContent());
        session.setStatus(interview.getStatus());
        session.setMessages(new ArrayList<>());
        return session;
    }

    private List<InterviewMessage> toInterviewMessages(InterviewSession session) {
        List<InterviewMessage> list = new ArrayList<>();
        if (session.getMessages() == null) {
            return list;
        }
        for (InterviewSession.SessionMessage item : session.getMessages()) {
            InterviewMessage message = new InterviewMessage();
            message.setInterviewId(session.getInterviewId());
            message.setRole(item.getRole());
            message.setContent(item.getContent());
            list.add(message);
        }
        return list;
    }

    private List<InterviewSession.SessionMessage> toSessionMessages(
            List<InterviewMessage> messages) {
        List<InterviewSession.SessionMessage> list = new ArrayList<>();
        if (messages == null) {
            return list;
        }
        for (InterviewMessage message : messages) {
            list.add(new InterviewSession.SessionMessage(
                    message.getRole(),
                    message.getContent()
            ));
        }
        return list;
    }

    private Resume requireParsedResume(Interview interview) {
        Resume resume = resumeService.getById(interview.getResumeId());

        if (resume == null) {
            throw new RuntimeException("简历不存在");
        }

        if (!resume.getUserId().equals(interview.getUserId())) {
            throw new ForbiddenException("无权使用该简历进行面试");
        }

        if (resume.getContent() == null || resume.getContent().isBlank()) {
            throw new RuntimeException("请先解析简历");
        }

        return resume;
    }

    private static int countUserMessages(List<InterviewMessage> history) {
        if (history == null) {
            return 0;
        }
        int rounds = 0;
        for (InterviewMessage message : history) {
            if (message.getRole() != null && "USER".equalsIgnoreCase(message.getRole())) {
                rounds++;
            }
        }
        return rounds;
    }

    static void requireRoundNotAdvanced(int expectedRound, int currentRound) {
        if (currentRound > expectedRound) {
            throw new RuntimeException("当前面试轮次已变化，请重新提交");
        }
    }

    /**
     * Redis 会话丢失时，用已经落库的消息恢复轮次和上一动作。不让模型重新猜阶段。
     */
    static void recoverState(InterviewSession session, List<InterviewMessage> messages) {
        int round = countUserMessages(messages);
        String lastRoute = null;
        String lastTool = null;
        if (messages != null) {
            for (InterviewMessage message : messages) {
                if (message.getRole() == null || !"AI".equalsIgnoreCase(message.getRole())) {
                    continue;
                }
                if (message.getRouteAction() != null && !message.getRouteAction().isBlank()) {
                    lastRoute = message.getRouteAction();
                }
                if (message.getToolStatus() != null && !message.getToolStatus().isBlank()) {
                    lastTool = message.getToolStatus();
                }
            }
        }
        session.setRound(round);
        session.setLastRouteAction(lastRoute);
        session.setLastToolStatus(lastTool == null ? "NONE" : lastTool);
        session.setStage(stageOf(lastRoute));
        session.setCurrentQuestion(findLatestAiQuestion(messages));
    }

    static String stageOf(String routeAction) {
        if ("probe".equals(routeAction)) {
            return "probing";
        }
        if ("switch".equals(routeAction)) {
            return "switching";
        }
        if ("conclude".equals(routeAction)) {
            return "concluding";
        }
        return "opening";
    }

    private static void requireScore(Integer score) {
        if (score == null || score < 0 || score > 100) {
            throw new RuntimeException("AI返回的面试报告不合法");
        }
    }

    private static String findLatestAiQuestion(List<InterviewMessage> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            InterviewMessage message = history.get(i);
            if ("AI".equalsIgnoreCase(message.getRole())) {
                String content = message.getContent();
                if (content == null) {
                    return null;
                }
                int idx = content.lastIndexOf("【追问】");
                if (idx >= 0) {
                    return content.substring(idx + "【追问】".length()).trim();
                }
                return content.trim();
            }
        }
        return null;
    }

    private String toJsonArray(List<String> list) {
        try {
            return objectMapper.writeValueAsString(
                    list == null ? Collections.emptyList() : list
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化面试报告失败", e);
        }
    }

    private record HistoryBundle(
            List<InterviewMessage> messages,
            String resumeContent,
            Optional<InterviewSession> session
    ) {
    }

    private record PersistedAnswer(
            String evaluation,
            InterviewMessage aiMessage,
            boolean shouldContinue
    ) {
    }
}
