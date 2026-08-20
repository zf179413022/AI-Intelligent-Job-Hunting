package com.aijob.server.controller;

import com.aijob.server.entity.Resume;
import com.aijob.server.entity.User;
import com.aijob.server.entity.ai.ResumeAiAnalysis;
import com.aijob.server.entity.ai.ResumeAnalysisResult;
import com.aijob.server.mapper.ResumeAiAnalysisMapper;
import com.aijob.server.mapper.UserMapper;
import com.aijob.server.security.LoginUserUtil;
import com.aijob.server.service.AiResumeService;
import com.aijob.server.service.ResumeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService resumeService;
    private final AiResumeService aiResumeService;
    private final LoginUserUtil loginUserUtil;
    private final UserMapper userMapper;
    private final ResumeAiAnalysisMapper resumeAiAnalysisMapper;

    public ResumeController(
            ResumeService resumeService,
            AiResumeService aiResumeService,
            LoginUserUtil loginUserUtil,
            UserMapper userMapper,
            ResumeAiAnalysisMapper resumeAiAnalysisMapper) {
        this.resumeService = resumeService;
        this.aiResumeService = aiResumeService;
        this.loginUserUtil = loginUserUtil;
        this.userMapper = userMapper;
        this.resumeAiAnalysisMapper = resumeAiAnalysisMapper;
    }

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Resume upload(
            @RequestParam("file") MultipartFile file) {

        String username = loginUserUtil.getUsername();

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
        );

        if (user == null) {
            throw new RuntimeException("当前用户不存在");
        }

        return resumeService.upload(file, user.getId());
    }

    @GetMapping
    public List<Resume> list() {

        String username = loginUserUtil.getUsername();

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
        );

        if (user == null) {
            throw new RuntimeException("当前用户不存在");
        }

        return resumeService.listByUserId(user.getId());
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {

        String username = loginUserUtil.getUsername();

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
        );

        if (user == null) {
            throw new RuntimeException("当前用户不存在");
        }

        resumeService.delete(id, user.getId());

        return "删除成功";
    }

    @PostMapping("/{id}/parse")
    public Resume parse(@PathVariable Long id) {

        String username = loginUserUtil.getUsername();

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
        );

        if (user == null) {
            throw new RuntimeException("当前用户不存在");
        }

        return resumeService.parse(id, user.getId());
    }

    @PostMapping("/{id}/ai-analyze")
    public ResumeAnalysisResult aiAnalyze(@PathVariable Long id) {

        String username = loginUserUtil.getUsername();

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
        );

        if (user == null) {
            throw new RuntimeException("当前用户不存在");
        }

        Resume resume = resumeService.getById(id);

        if (resume == null) {
            throw new RuntimeException("简历不存在");
        }

        if (!resume.getUserId().equals(user.getId())) {
            throw new RuntimeException("无权访问该简历");
        }

        if (resume.getContent() == null || resume.getContent().isBlank()) {
            throw new RuntimeException("请先解析简历");
        }

        // 调用 Python + DeepSeek
        ResumeAnalysisResult result =
                aiResumeService.analyze(resume.getContent());

        // 保存 AI 分析结果
        ResumeAiAnalysis analysis = new ResumeAiAnalysis();

        analysis.setResumeId(resume.getId());
        analysis.setUserId(user.getId());
        analysis.setName(result.getName());
        analysis.setScore(result.getScore());

        if (result.getSkills() != null) {
            analysis.setSkills(
                    String.join(",", result.getSkills())
            );
        }

        if (result.getSuggestions() != null) {
            analysis.setSuggestions(
                    String.join(",", result.getSuggestions())
            );
        }

        resumeAiAnalysisMapper.insert(analysis);

        return result;
    }

    @GetMapping("/{id}/ai-analysis")
    public ResumeAiAnalysis getAiAnalysis(@PathVariable Long id) {

        String username = loginUserUtil.getUsername();

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
        );

        if (user == null) {
            throw new RuntimeException("当前用户不存在");
        }

        Resume resume = resumeService.getById(id);

        if (resume == null) {
            throw new RuntimeException("简历不存在");
        }

        if (!resume.getUserId().equals(user.getId())) {
            throw new RuntimeException("无权访问该简历");
        }

        ResumeAiAnalysis analysis = resumeAiAnalysisMapper.selectOne(
                new LambdaQueryWrapper<ResumeAiAnalysis>()
                        .eq(ResumeAiAnalysis::getResumeId, id)
                        .eq(ResumeAiAnalysis::getUserId, user.getId())
                        .orderByDesc(ResumeAiAnalysis::getCreatedAt)
                        .last("LIMIT 1")
        );

        if (analysis == null) {
            throw new RuntimeException("该简历暂无AI分析结果");
        }

        return analysis;
    }
}
