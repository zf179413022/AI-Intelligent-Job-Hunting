package com.aijob.server.service.impl;

import com.aijob.server.ai.DeepSeekJsonChat;
import com.aijob.server.ai.InterviewTurnEvents;
import com.aijob.server.ai.InterviewTurnGraph;
import com.aijob.server.ai.InterviewTurnInput;
import com.aijob.server.ai.ModelJsonParser;
import com.aijob.server.entity.InterviewMessage;
import com.aijob.server.entity.ai.InterviewAnswerAiResult;
import com.aijob.server.entity.ai.InterviewReportAiResult;
import com.aijob.server.entity.ai.InterviewStartAiResult;
import com.aijob.server.service.AiInterviewService;
import com.aijob.server.service.InterviewAnswerStreamHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiInterviewServiceImpl implements AiInterviewService {

    private static final String JSON_SYSTEM =
            "你是专业的 IT 技术面试官。始终只返回合法 JSON，不要使用 markdown。";

    private static final String REPORT_SYSTEM =
            "你是专业的 IT 面试官，负责生成结构化面试报告。始终只返回合法 JSON。";

    private static final double TEMPERATURE = 0.3;

    private static final int RECENT_USER_ROUNDS = 3;

    private static final int RESUME_BRIEF_LENGTH = 3000;

    private static final long STREAM_WAIT_SECONDS = 150;

    private static final Runnable STREAM_END = () -> {
    };

    private final DeepSeekJsonChat deepSeekJsonChat;
    private final InterviewTurnGraph interviewTurnGraph;

    @Override
    public InterviewStartAiResult start(String resumeContent, String position) {
        String resume = requireText(resumeContent, "resumeContent 不能为空");
        String job = requireText(position, "position 不能为空");

        InterviewStartAiResult result = deepSeekJsonChat.complete(
                JSON_SYSTEM,
                startPrompt(job, resume),
                TEMPERATURE,
                InterviewStartAiResult.class
        );
        if (result == null || result.getQuestion() == null || result.getQuestion().isBlank()) {
            throw new RuntimeException("AI未返回有效面试题");
        }
        result.setQuestion(result.getQuestion().strip());
        return result;
    }

    @Override
    public InterviewAnswerAiResult answer(
            String resumeContent,
            String position,
            List<InterviewMessage> history,
            String currentQuestion,
            String userAnswer,
            Long userId,
            Long interviewId) {

        return interviewTurnGraph.run(turnInput(
                resumeContent, position, history, currentQuestion, userAnswer, userId, interviewId
        ));
    }

    @Override
    public void answerStream(
            String resumeContent,
            String position,
            List<InterviewMessage> history,
            String currentQuestion,
            String userAnswer,
            Long userId,
            Long interviewId,
            InterviewAnswerStreamHandler handler) {

        InterviewTurnInput input = turnInput(
                resumeContent, position, history, currentQuestion, userAnswer, userId, interviewId
        );
        BlockingQueue<Runnable> signals = new LinkedBlockingQueue<>();
        // 只表示收尾动作已入队。SSE 断开不能占用这个标记，否则本轮结果不会落库。
        AtomicBoolean terminalQueued = new AtomicBoolean(false);
        AtomicBoolean drainStopped = new AtomicBoolean(false);
        Thread.ofVirtual().name("interview-model").start(() -> {
            try {
                InterviewAnswerAiResult result = interviewTurnGraph.run(input, new InterviewTurnEvents() {
                    @Override
                    public void onTool(String name, String status) {
                        if (drainStopped.get()) {
                            return;
                        }
                        signals.add(() -> handler.onTool(name, status));
                    }

                    @Override
                    public void onRoute(String action) {
                        if (drainStopped.get()) {
                            return;
                        }
                        signals.add(() -> handler.onRoute(action));
                    }

                    @Override
                    public void onDelta(String content) {
                        if (drainStopped.get() || content == null || content.isEmpty()) {
                            return;
                        }
                        signals.add(() -> handler.onDelta(content));
                    }
                });
                finishWith(signals, terminalQueued, () -> handler.onDone(result));
            } catch (RuntimeException e) {
                String message = "AI未返回下一题".equals(e.getMessage())
                        ? e.getMessage()
                        : "AI面试流式调用失败";
                finishWith(signals, terminalQueued, () -> handler.onError(message));
            }
        });
        drain(signals, handler, terminalQueued, drainStopped);
    }

    @Override
    public InterviewReportAiResult report(
            String resumeContent,
            String position,
            List<InterviewMessage> history) {

        String resume = requireText(resumeContent, "resumeContent 不能为空");
        String job = requireText(position, "position 不能为空");

        String prompt = reportPrompt(job, brief(resume), formatHistory(history));
        InterviewReportAiResult result = deepSeekJsonChat.completeValid(
                REPORT_SYSTEM,
                prompt,
                TEMPERATURE,
                InterviewReportAiResult.class,
                AiInterviewServiceImpl::validReport,
                "AI返回的面试报告不合法"
        );
        if (result == null) {
            throw new RuntimeException("AI未返回有效面试报告");
        }
        return result;
    }

    static boolean validReport(com.fasterxml.jackson.databind.JsonNode node) {
        return ModelJsonParser.integralScore(node, "totalScore")
                && ModelJsonParser.integralScore(node, "javaScore")
                && ModelJsonParser.integralScore(node, "mysqlScore")
                && ModelJsonParser.integralScore(node, "redisScore")
                && ModelJsonParser.integralScore(node, "springScore")
                && ModelJsonParser.textPresent(node, "summary");
    }

    private void drain(
            BlockingQueue<Runnable> signals,
            InterviewAnswerStreamHandler handler,
            AtomicBoolean terminalQueued,
            AtomicBoolean drainStopped) {
        try {
            while (true) {
                Runnable signal = signals.poll(STREAM_WAIT_SECONDS, TimeUnit.SECONDS);
                if (signal == null) {
                    if (terminalQueued.compareAndSet(false, true)) {
                        handler.onError("AI面试流式调用超时");
                    }
                    return;
                }
                if (signal == STREAM_END) {
                    return;
                }
                try {
                    signal.run();
                } catch (RuntimeException e) {
                    // 通知失败不能结束本轮。后面的 onDone 仍要执行落库。
                    log.debug("流式通知失败，继续等待本轮结果。error={}", e.getClass().getSimpleName());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (terminalQueued.compareAndSet(false, true)) {
                handler.onError("AI面试流式调用被中断");
            }
        } finally {
            drainStopped.set(true);
        }
    }

    /**
     * 入队的是业务收尾，不是 SSE 开关。同一轮只收尾一次，避免超时和正常完成各写一遍。
     */
    private static void finishWith(
            BlockingQueue<Runnable> signals,
            AtomicBoolean terminalQueued,
            Runnable action) {
        if (!terminalQueued.compareAndSet(false, true)) {
            return;
        }
        signals.add(action);
        signals.add(STREAM_END);
    }

    private static String startPrompt(String position, String resume) {
        return """
                你是一名严格但友好的 IT 技术面试官，正在面试「%s」岗位候选人。

                根据候选人简历，生成第一道开场技术面试题。

                约束：
                1. 只能基于简历中明确出现的技术栈/项目出题，不得虚构简历没有的技能。
                2. 第一题适合开场：可结合自我介绍或简历中的核心技术（如 Java / Spring Boot / MySQL）。
                3. 只出一道题，问题清晰具体，便于口头回答。
                4. 只返回合法 JSON，不要 markdown。

                简历：
                %s

                返回：
                {
                  "question": "第一道面试题"
                }
                """.formatted(position, resume);
    }

    private static InterviewTurnInput turnInput(
            String resumeContent,
            String position,
            List<InterviewMessage> history,
            String currentQuestion,
            String userAnswer,
            Long userId,
            Long interviewId) {
        String resume = requireText(resumeContent, "resumeContent 不能为空");
        String job = requireText(position, "position 不能为空");
        String question = requireText(currentQuestion, "currentQuestion 不能为空");
        String answer = requireText(userAnswer, "userAnswer 不能为空");
        return new InterviewTurnInput(
                job,
                brief(resume),
                formatHistory(recentHistory(history, RECENT_USER_ROUNDS)),
                question,
                answer,
                countUserRounds(history),
                userId,
                interviewId
        );
    }

    /**
     * 只把最近若干轮送进模型。完整对话仍留在 Redis / MySQL，轮次上限按全量用户回答计算。
     */
    static List<InterviewMessage> recentHistory(List<InterviewMessage> history, int userRounds) {
        if (history == null || history.isEmpty() || userRounds <= 0) {
            return List.of();
        }
        int seen = 0;
        int start = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            InterviewMessage message = history.get(i);
            if (message.getRole() != null && "USER".equalsIgnoreCase(message.getRole())) {
                seen++;
                if (seen == userRounds) {
                    start = i;
                    if (i > 0 && history.get(i - 1).getRole() != null
                            && "AI".equalsIgnoreCase(history.get(i - 1).getRole())) {
                        start = i - 1;
                    }
                    break;
                }
            }
        }
        return List.copyOf(history.subList(start, history.size()));
    }

    private static String reportPrompt(String position, String resume, String history) {
        return """
                你是一名资深 IT 技术面试官，请根据完整面试记录为「%s」岗位生成结构化面试报告。

                约束：
                1. 只能依据面试对话与简历中明确信息评分，不得虚构表现。
                2. 分数均为 0-100 的整数。
                3. weakPoints / suggestions 为字符串数组。
                4. 只返回合法 JSON，不要 markdown。

                简历：
                %s

                完整面试记录：
                %s

                返回：
                {
                  "totalScore": 78,
                  "javaScore": 80,
                  "mysqlScore": 75,
                  "redisScore": 70,
                  "springScore": 82,
                  "weakPoints": ["薄弱点1"],
                  "suggestions": ["建议1"],
                  "summary": "总体评价"
                }
                """.formatted(position, resume, history);
    }

    private static String formatHistory(List<InterviewMessage> history) {
        if (history == null || history.isEmpty()) {
            return "（暂无历史对话）";
        }
        StringBuilder builder = new StringBuilder();
        for (InterviewMessage message : history) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            String role = message.getRole() == null ? "" : message.getRole().strip().toUpperCase();
            String label = "AI".equals(role) ? "面试官" : "候选人";
            String content = message.getContent() == null ? "" : message.getContent().strip();
            builder.append(label).append('：').append(content);
        }
        return builder.toString();
    }

    private static int countUserRounds(List<InterviewMessage> history) {
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

    private static String brief(String text) {
        return text.length() <= RESUME_BRIEF_LENGTH ? text : text.substring(0, RESUME_BRIEF_LENGTH);
    }

    private static String requireText(String value, String message) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            throw new RuntimeException(message);
        }
        return text;
    }
}
