package com.aijob.server.service.impl;

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
import com.aijob.server.service.InterviewMessageService;
import com.aijob.server.service.InterviewService;
import com.aijob.server.service.ResumeService;
import com.aijob.server.vo.InterviewAnswerVO;
import com.aijob.server.vo.InterviewStartVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {

    private final InterviewMapper interviewMapper;
    private final InterviewReportMapper interviewReportMapper;
    private final InterviewMessageService interviewMessageService;
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

        InterviewMessage questionMessage = interviewMessageService.save(
                interview.getId(),
                "AI",
                aiResult.getQuestion().trim()
        );

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
        List<InterviewMessage> history =
                interviewMessageService.listByInterviewId(interview.getId());

        String currentQuestion = findLatestAiQuestion(history);
        if (currentQuestion == null) {
            throw new RuntimeException("未找到当前面试题，请重新开始面试");
        }

        InterviewAnswerAiResult aiResult = aiInterviewService.answer(
                resume.getContent(),
                interview.getPosition(),
                history,
                currentQuestion,
                answer.trim()
        );

        if (aiResult == null) {
            throw new RuntimeException("AI面试回答分析失败");
        }

        interviewMessageService.save(interview.getId(), "USER", answer.trim());

        String evaluation = aiResult.getEvaluation() == null
                ? ""
                : aiResult.getEvaluation().trim();
        boolean shouldContinue = Boolean.TRUE.equals(aiResult.getShouldContinue());
        String nextQuestion = aiResult.getNextQuestion() == null
                ? ""
                : aiResult.getNextQuestion().trim();

        InterviewMessage nextQuestionMessage = null;
        if (shouldContinue && !nextQuestion.isBlank()) {
            String aiContent = evaluation.isBlank()
                    ? nextQuestion
                    : "【点评】" + evaluation + "\n\n【追问】" + nextQuestion;
            nextQuestionMessage = interviewMessageService.save(
                    interview.getId(),
                    "AI",
                    aiContent
            );
        } else if (!evaluation.isBlank()) {
            nextQuestionMessage = interviewMessageService.save(
                    interview.getId(),
                    "AI",
                    "【点评】" + evaluation + "\n\n本轮面试问题已足够，可以结束并生成报告。"
            );
            shouldContinue = false;
        }

        return new InterviewAnswerVO(
                interviewMapper.selectById(id),
                evaluation,
                nextQuestionMessage,
                shouldContinue
        );
    }

    @Override
    @Transactional
    public InterviewReport finish(Long id, Long userId) {
        Interview interview = getById(id, userId);

        if (!"RUNNING".equals(interview.getStatus())) {
            throw new RuntimeException("当前面试无法结束");
        }

        Resume resume = requireParsedResume(interview);
        List<InterviewMessage> history =
                interviewMessageService.listByInterviewId(interview.getId());

        if (history.isEmpty()) {
            throw new RuntimeException("尚无面试对话，无法生成报告");
        }

        InterviewReportAiResult aiResult = aiInterviewService.report(
                resume.getContent(),
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
}
