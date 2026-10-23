package com.aijob.server.service.impl;

import com.aijob.server.exception.ForbiddenException;

import com.aijob.server.dto.JobMatchRequest;
import com.aijob.server.entity.JobMatch;
import com.aijob.server.entity.Resume;
import com.aijob.server.entity.ai.JobMatchAiResult;
import com.aijob.server.mapper.JobMatchMapper;
import com.aijob.server.service.AiJobMatchService;
import com.aijob.server.service.JobMatchService;
import com.aijob.server.service.ResumeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JobMatchServiceImpl implements JobMatchService {

    private final JobMatchMapper jobMatchMapper;
    private final ResumeService resumeService;
    private final AiJobMatchService aiJobMatchService;
    private final ObjectMapper objectMapper;

    @Override
    public JobMatch create(JobMatchRequest request, Long userId) {

        Resume resume = resumeService.getById(request.getResumeId());

        if (resume == null) {
            throw new RuntimeException("简历不存在");
        }

        if (!resume.getUserId().equals(userId)) {
            throw new ForbiddenException("无权使用该简历");
        }

        if (resume.getContent() == null || resume.getContent().isBlank()) {
            throw new RuntimeException("请先解析简历");
        }

        JobMatchAiResult aiResult = aiJobMatchService.match(
                resume.getContent(),
                request.getJobName(),
                request.getCompanyName(),
                request.getJobDescription()
        );

        if (aiResult == null) {
            throw new RuntimeException("AI 岗位匹配返回为空");
        }

        Integer matchScore = aiResult.getMatchScore();
        if (matchScore == null || matchScore < 0 || matchScore > 100) {
            throw new RuntimeException("AI返回的匹配度不合法");
        }

        JobMatch record = new JobMatch();
        record.setUserId(userId);
        record.setResumeId(resume.getId());
        record.setJobName(request.getJobName().trim());
        record.setCompanyName(
                request.getCompanyName() == null ? null : request.getCompanyName().trim()
        );
        record.setJobDescription(request.getJobDescription().trim());
        record.setMatchScore(matchScore);
        record.setMatchedSkills(toJsonArray(aiResult.getMatchedSkills()));
        record.setMissingSkills(toJsonArray(aiResult.getMissingSkills()));
        record.setAdvantages(toJsonArray(aiResult.getAdvantages()));
        record.setRisks(toJsonArray(aiResult.getRisks()));
        record.setSuggestions(toJsonArray(aiResult.getSuggestions()));
        record.setSummary(aiResult.getSummary());

        jobMatchMapper.insert(record);

        return record;
    }

    @Override
    public List<JobMatch> listByUserId(Long userId) {
        return jobMatchMapper.selectList(
                new LambdaQueryWrapper<JobMatch>()
                        .eq(JobMatch::getUserId, userId)
                        .orderByDesc(JobMatch::getCreatedAt)
        );
    }

    @Override
    public JobMatch getById(Long id, Long userId) {
        JobMatch record = jobMatchMapper.selectById(id);

        if (record == null) {
            throw new RuntimeException("岗位匹配记录不存在");
        }

        if (!record.getUserId().equals(userId)) {
            throw new ForbiddenException("无权访问该记录");
        }

        return record;
    }

    @Override
    public void delete(Long id, Long userId) {
        JobMatch record = getById(id, userId);
        jobMatchMapper.deleteById(record.getId());
    }

    private String toJsonArray(List<String> list) {
        try {
            return objectMapper.writeValueAsString(
                    list == null ? Collections.emptyList() : list
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化匹配结果失败", e);
        }
    }
}
