package com.aijob.server.controller;

import com.aijob.server.entity.Resume;
import com.aijob.server.entity.User;
import com.aijob.server.entity.ai.ResumeAnalysisResult;
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

    public ResumeController(
            ResumeService resumeService,
            AiResumeService aiResumeService,
            LoginUserUtil loginUserUtil,
            UserMapper userMapper) {
        this.resumeService = resumeService;
        this.aiResumeService = aiResumeService;
        this.loginUserUtil = loginUserUtil;
        this.userMapper = userMapper;
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

        return aiResumeService.analyze(resume.getContent());
    }
}
