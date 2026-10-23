package com.aijob.server.controller;

import com.aijob.server.dto.JobMatchRequest;
import com.aijob.server.entity.JobMatch;
import com.aijob.server.entity.User;
import com.aijob.server.mapper.UserMapper;
import com.aijob.server.security.LoginUserUtil;
import com.aijob.server.service.JobMatchService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/job-matches")
public class JobMatchController {

    private final JobMatchService jobMatchService;
    private final LoginUserUtil loginUserUtil;
    private final UserMapper userMapper;

    public JobMatchController(
            JobMatchService jobMatchService,
            LoginUserUtil loginUserUtil,
            UserMapper userMapper) {
        this.jobMatchService = jobMatchService;
        this.loginUserUtil = loginUserUtil;
        this.userMapper = userMapper;
    }

    @PostMapping
    public JobMatch create(@Valid @RequestBody JobMatchRequest request) {
        return jobMatchService.create(request, currentUser().getId());
    }

    @GetMapping
    public List<JobMatch> list() {
        return jobMatchService.listByUserId(currentUser().getId());
    }

    @GetMapping("/{id}")
    public JobMatch detail(@PathVariable Long id) {
        return jobMatchService.getById(id, currentUser().getId());
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        jobMatchService.delete(id, currentUser().getId());
        return "删除成功";
    }

    private User currentUser() {
        String username = loginUserUtil.getUsername();

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
        );

        if (user == null) {
            throw new RuntimeException("当前用户不存在");
        }

        return user;
    }
}
