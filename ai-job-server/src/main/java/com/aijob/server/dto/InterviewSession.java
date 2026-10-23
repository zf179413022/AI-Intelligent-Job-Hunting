package com.aijob.server.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis 中的面试临时会话（进行中上下文）。
 */
@Data
public class InterviewSession {

    private Long interviewId;

    private Long userId;

    private Long resumeId;

    private String position;

    private String resumeContent;

    private String status;

    /** 已经提交的用户回答轮次。Graph 不跨请求保存，这个字段才是下一轮看到的进度。 */
    private Integer round;

    /** opening / probing / switching / concluding */
    private String stage;

    private String currentQuestion;

    private String lastRouteAction;

    private String lastToolStatus;

    private List<SessionMessage> messages = new ArrayList<>();

    @Data
    public static class SessionMessage {
        private String role;
        private String content;

        public SessionMessage() {
        }

        public SessionMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
