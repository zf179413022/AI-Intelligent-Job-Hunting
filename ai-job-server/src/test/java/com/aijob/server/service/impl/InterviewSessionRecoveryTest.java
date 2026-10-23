package com.aijob.server.service.impl;

import com.aijob.server.dto.InterviewSession;
import com.aijob.server.entity.InterviewMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InterviewSessionRecoveryTest {

    @Test
    void rebuildsRoundStageAndLastActionFromMessages() {
        List<InterviewMessage> messages = new ArrayList<>();
        messages.add(ai("介绍一下你的项目", null, null));
        messages.add(user("回答1"));
        messages.add(ai("【点评】可以\n\n【追问】第二题", "switch", "NONE"));
        messages.add(user("回答2"));
        messages.add(ai("【点评】再挖一点\n\n【追问】HashMap 怎么扩容", "probe", "SUCCESS"));
        messages.add(user("回答3"));
        messages.add(ai("【点评】继续\n\n【追问】还要讲清扩容", "probe", "FAILED"));

        InterviewSession session = new InterviewSession();
        InterviewServiceImpl.recoverState(session, messages);

        assertEquals(3, session.getRound());
        assertEquals("probing", session.getStage());
        assertEquals("probe", session.getLastRouteAction());
        assertEquals("FAILED", session.getLastToolStatus());
        assertEquals("还要讲清扩容", session.getCurrentQuestion());
    }

    @Test
    void rejectsARoundThatAnotherRequestAlreadyAdvanced() {
        assertThrows(RuntimeException.class, () -> InterviewServiceImpl.requireRoundNotAdvanced(2, 3));
        InterviewServiceImpl.requireRoundNotAdvanced(2, 2);
    }

    private static InterviewMessage ai(String content, String routeAction, String toolStatus) {
        InterviewMessage message = new InterviewMessage();
        message.setRole("AI");
        message.setContent(content);
        message.setRouteAction(routeAction);
        message.setToolStatus(toolStatus);
        return message;
    }

    private static InterviewMessage user(String content) {
        InterviewMessage message = new InterviewMessage();
        message.setRole("USER");
        message.setContent(content);
        return message;
    }
}
