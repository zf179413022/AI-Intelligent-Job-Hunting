package com.aijob.server.controller;

import com.aijob.server.dto.KnowledgeAiChatRequest;
import com.aijob.server.dto.KnowledgeAskRequest;
import com.aijob.server.entity.User;
import com.aijob.server.mapper.UserMapper;
import com.aijob.server.security.LoginUserUtil;
import com.aijob.server.service.KnowledgeAiService;
import com.aijob.server.service.KnowledgeRagService;
import com.aijob.server.vo.KnowledgeAiChatVO;
import com.aijob.server.vo.KnowledgeAskVO;
import com.aijob.server.vo.KnowledgeRetrieveVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * 知识库 AI：
 * - /chat：5.3 DeepSeek 冒烟（非 RAG）
 * - /retrieve：5.6.1 Top-K 检索
 * - /ask：5.6 同步 RAG
 * - /ask/stream：5.9 SSE 流式 RAG
 */
@RestController
@RequestMapping("/api/knowledge/ai")
public class KnowledgeAiController {

    private final KnowledgeAiService knowledgeAiService;
    private final KnowledgeRagService knowledgeRagService;
    private final LoginUserUtil loginUserUtil;
    private final UserMapper userMapper;
    private final ExecutorService sseExecutor;

    public KnowledgeAiController(
            KnowledgeAiService knowledgeAiService,
            KnowledgeRagService knowledgeRagService,
            LoginUserUtil loginUserUtil,
            UserMapper userMapper,
            ExecutorService sseExecutor) {
        this.knowledgeAiService = knowledgeAiService;
        this.knowledgeRagService = knowledgeRagService;
        this.loginUserUtil = loginUserUtil;
        this.userMapper = userMapper;
        this.sseExecutor = sseExecutor;
    }

    @PostMapping("/chat")
    public KnowledgeAiChatVO chat(@Valid @RequestBody KnowledgeAiChatRequest request) {
        String answer = knowledgeAiService.chat(request.getPrompt());
        return new KnowledgeAiChatVO(
                request.getPrompt(),
                answer,
                "langchain4j-openai-compatible/deepseek"
        );
    }

    @PostMapping("/retrieve")
    public KnowledgeRetrieveVO retrieve(@Valid @RequestBody KnowledgeAskRequest request) {
        return knowledgeRagService.retrieve(request, currentUser().getId());
    }

    @PostMapping("/ask")
    public KnowledgeAskVO ask(@Valid @RequestBody KnowledgeAskRequest request) {
        return knowledgeRagService.ask(request, currentUser().getId());
    }

    /**
     * SSE：event=meta|delta|done|error
     */
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@Valid @RequestBody KnowledgeAskRequest request) {
        Long userId = currentUser().getId();
        SecurityContext securityContext = SecurityContextHolder.getContext();
        SseEmitter emitter = new SseEmitter(180_000L);

        Runnable task = () -> {
            try {
                knowledgeRagService.askStream(request, userId, emitter);
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of(
                                    "message",
                                    e.getMessage() == null ? "流式 RAG 失败" : e.getMessage()
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
