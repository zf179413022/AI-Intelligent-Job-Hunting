package com.aijob.server.service.impl;

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
            throw new RuntimeException("无权使用该简历进行面试");
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
            throw new RuntimeException("无权访问该面试");
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
        session.getMessages().add(new InterviewSession.SessionMessage("AI", question));
        interviewSessionService.save(session);

        return new InterviewStartVO(interviewMapper.selectById(id), questionMessage);
    }

    @Override
    @Transactional
    public InterviewAnswerVO answer(Long id, Long userId, String answer) {
        Interview interview = getById(id, userId);

        if (!"RUNNING".equals(interview.getStatus())) {
            throw new RuntimeException("当前面试未在进行中，无法回答");
        }

        if (answer == null || answer.isBlank()) {
            throw new RuntimeException("回答内容不能为空");
        }

        Resume resume = requireParsedResume(interview);
        HistoryBundle historyBundle = loadHistoryForAi(interview, resume);
        List<InterviewMessage> history = historyBundle.messages();

        String currentQuestion = findLatestAiQuestion(history);
        if (currentQuestion == null) {
            throw new RuntimeException("未找到当前面试题，请重新开始面试");
        }

        InterviewAnswerAiResult aiResult = aiInterviewService.answer(
                historyBundle.resumeContent(),
                interview.getPosition(),
                history,
                currentQuestion,
                answer.trim()
        );

        if (aiResult == null) {
            throw new RuntimeException("AI面试回答分析失败");
        }

        PersistedAnswer persisted = persistAnswerResult(
                interview, resume, historyBundle, history, answer.trim(), aiResult
        );

        return new InterviewAnswerVO(
                interviewMapper.selectById(id),
                persisted.evaluation(),
                persisted.aiMessage(),
                persisted.shouldContinue()
        );
    }

    @Override
    public void answerStream(Long id, Long userId, String answer, SseEmitter emitter) {
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

            Resume resume = requireParsedResume(interview);
            HistoryBundle historyBundle = loadHistoryForAi(interview, resume);
            List<InterviewMessage> history = historyBundle.messages();

            String currentQuestion = findLatestAiQuestion(history);
            if (currentQuestion == null) {
                sendSseError(emitter, "未找到当前面试题，请重新开始面试");
                return;
            }

            final String trimmedAnswer = answer.trim();
            aiInterviewService.answerStream(
                    historyBundle.resumeContent(),
                    interview.getPosition(),
                    history,
                    currentQuestion,
                    trimmedAnswer,
                    new InterviewAnswerStreamHandler() {
                        @Override
                        public void onDelta(String content) {
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("delta")
                                        .data(Map.of("content", content)));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }

                        @Override
                        public void onDone(InterviewAnswerAiResult aiResult) {
                            try {
                                if (aiResult == null) {
                                    sendSseError(emitter, "AI面试回答分析失败");
                                    return;
                                }
                                PersistedAnswer persisted = persistAnswerResult(
                                        interview,
                                        resume,
                                        historyBundle,
                                        history,
                                        trimmedAnswer,
                                        aiResult
                                );
                                Map<String, Object> payload = new LinkedHashMap<>();
                                payload.put("evaluation", persisted.evaluation());
                                payload.put(
                                        "nextQuestion",
                                        aiResult.getNextQuestion() == null
                                                ? ""
                                                : aiResult.getNextQuestion().trim()
                                );
                                payload.put("shouldContinue", persisted.shouldContinue());
                                payload.put("message", persisted.aiMessage());
                                payload.put("interview", interviewMapper.selectById(id));
                                emitter.send(SseEmitter.event().name("done").data(payload));
                                emitter.complete();
                            } catch (Exception e) {
                                sendSseError(emitter, e.getMessage() == null
                                        ? "流式面试落库失败"
                                        : e.getMessage());
                            }
                        }

                        @Override
                        public void onError(String message) {
                            sendSseError(emitter, message == null ? "AI流式面试失败" : message);
                        }
                    }
            );
        } catch (Exception e) {
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
            InterviewAnswerAiResult aiResult) {

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
                    aiPersistContent
            );
        } else if (!evaluation.isBlank()) {
            aiPersistContent = "【点评】" + evaluation + "\n\n本轮面试问题已足够，可以结束并生成报告。";
            nextQuestionMessage = interviewMessageService.save(
                    interview.getId(),
                    "AI",
                    aiPersistContent
            );
            shouldContinue = false;
        }

        InterviewSession session = historyBundle.session().orElseGet(
                () -> newSession(interview, resume)
        );
        session.setStatus("RUNNING");
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

        if (aiResult == null || aiResult.getTotalScore() == null) {
            throw new RuntimeException("AI未返回有效面试报告");
        }

        Integer totalScore = aiResult.getTotalScore();
        if (totalScore < 0 || totalScore > 100) {
            throw new RuntimeException("AI返回的面试总分不合法");
        }

        InterviewReport existing = interviewReportMapper.selectOne(
                new LambdaQueryWrapper<InterviewReport>()
                        .eq(InterviewReport::getInterviewId, interview.getId())
        );

        InterviewReport report = existing == null ? new InterviewReport() : existing;
        report.setInterviewId(interview.getId());
        report.setTotalScore(totalScore);
        report.setJavaScore(normalizeScore(aiResult.getJavaScore()));
        report.setMysqlScore(normalizeScore(aiResult.getMysqlScore()));
        report.setRedisScore(normalizeScore(aiResult.getRedisScore()));
        report.setSpringScore(normalizeScore(aiResult.getSpringScore()));
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
            log.debug("面试上下文来自 Redis, interviewId={}", interview.getId());
            return new HistoryBundle(toInterviewMessages(session), resumeContent, cached);
        }

        log.debug("面试上下文回退 MySQL, interviewId={}", interview.getId());
        List<InterviewMessage> fromDb =
                interviewMessageService.listByInterviewId(interview.getId());

        InterviewSession session = newSession(interview, resume);
        session.setStatus(interview.getStatus());
        session.setMessages(toSessionMessages(fromDb));
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
            throw new RuntimeException("无权使用该简历进行面试");
        }

        if (resume.getContent() == null || resume.getContent().isBlank()) {
            throw new RuntimeException("请先解析简历");
        }

        return resume;
    }

    private String findLatestAiQuestion(List<InterviewMessage> history) {
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

    private Integer normalizeScore(Integer score) {
        if (score == null) {
            return 0;
        }
        if (score < 0) {
            return 0;
        }
        if (score > 100) {
            return 100;
        }
        return score;
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
