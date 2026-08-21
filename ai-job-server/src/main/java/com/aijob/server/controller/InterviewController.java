package com.aijob.server.controller;

import com.aijob.server.dto.InterviewAnswerRequest;
import com.aijob.server.dto.InterviewCreateRequest;
import com.aijob.server.entity.Interview;
import com.aijob.server.entity.InterviewMessage;
import com.aijob.server.entity.InterviewReport;
import com.aijob.server.entity.User;
import com.aijob.server.mapper.UserMapper;
import com.aijob.server.security.LoginUserUtil;
import com.aijob.server.service.InterviewService;
import com.aijob.server.vo.InterviewAnswerVO;
import com.aijob.server.vo.InterviewStartVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    private final InterviewService interviewService;
    private final LoginUserUtil loginUserUtil;
    private final UserMapper userMapper;

    public InterviewController(
            InterviewService interviewService,
            LoginUserUtil loginUserUtil,
            UserMapper userMapper) {
        this.interviewService = interviewService;
        this.loginUserUtil = loginUserUtil;
        this.userMapper = userMapper;
    }

    @PostMapping
    public Interview create(@Valid @RequestBody InterviewCreateRequest request) {
        return interviewService.create(
                currentUser().getId(),
                request.getResumeId(),
                request.getPosition()
        );
    }

    @PostMapping("/{id}/start")
    public InterviewStartVO start(@PathVariable Long id) {
        return interviewService.start(id, currentUser().getId());
    }

    @PostMapping("/{id}/answer")
    public InterviewAnswerVO answer(
            @PathVariable Long id,
            @Valid @RequestBody InterviewAnswerRequest request) {
        return interviewService.answer(id, currentUser().getId(), request.getAnswer());
    }

    @PostMapping("/{id}/finish")
    public InterviewReport finish(@PathVariable Long id) {
        return interviewService.finish(id, currentUser().getId());
    }

    @GetMapping
    public List<Interview> list() {
        return interviewService.listByUserId(currentUser().getId());
    }

    @GetMapping("/{id}")
    public Interview detail(@PathVariable Long id) {
        return interviewService.getById(id, currentUser().getId());
    }

    @GetMapping("/{id}/messages")
    public List<InterviewMessage> messages(@PathVariable Long id) {
        return interviewService.listMessages(id, currentUser().getId());
    }

    @GetMapping("/{id}/report")
    public InterviewReport report(@PathVariable Long id) {
        return interviewService.getReport(id, currentUser().getId());
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        interviewService.delete(id, currentUser().getId());
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
