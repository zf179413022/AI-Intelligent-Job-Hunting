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
