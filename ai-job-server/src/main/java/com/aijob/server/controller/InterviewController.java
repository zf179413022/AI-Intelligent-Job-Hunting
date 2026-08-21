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
import org.springframework.http.MediaType;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ExecutorService;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    private final InterviewService interviewService;
    private final LoginUserUtil loginUserUtil;
    private final UserMapper userMapper;
    private final ExecutorService sseExecutor;

    public InterviewController(
            InterviewService interviewService,
            LoginUserUtil loginUserUtil,
            UserMapper userMapper,
            ExecutorService sseExecutor) {
        this.interviewService = interviewService;
        this.loginUserUtil = loginUserUtil;
        this.userMapper = userMapper;
        this.sseExecutor = sseExecutor;
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

    /**
     * SSE 流式回答：event=delta|done|error
     */
    @PostMapping(value = "/{id}/answer/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter answerStream(
            @PathVariable Long id,
            @Valid @RequestBody InterviewAnswerRequest request) {
        Long userId = currentUser().getId();
        String answer = request.getAnswer();
        SecurityContext securityContext = SecurityContextHolder.getContext();
        SseEmitter emitter = new SseEmitter(180_000L);

        Runnable task = () -> {
            try {
                interviewService.answerStream(id, userId, answer, emitter);
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(java.util.Map.of(
                                    "message",
                                    e.getMessage() == null ? "流式面试失败" : e.getMessage()
                            )));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            }
        };

        sseExecutor.execute(new DelegatingSecurityContextRunnable(task, securityContext));
        return emitter;
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
