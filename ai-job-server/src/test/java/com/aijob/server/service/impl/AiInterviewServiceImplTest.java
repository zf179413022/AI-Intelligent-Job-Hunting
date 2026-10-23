package com.aijob.server.service.impl;

import com.aijob.server.entity.InterviewMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiInterviewServiceImplTest {

    @Test
    void keepsOnlyTheLatestRoundsForTheModel() {
        List<InterviewMessage> history = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            history.add(message("AI", "问题" + i));
            history.add(message("USER", "回答" + i));
        }

        List<InterviewMessage> recent = AiInterviewServiceImpl.recentHistory(history, 3);

        assertEquals("问题2", recent.getFirst().getContent());
        assertEquals(6, recent.size());
        assertEquals("回答4", recent.getLast().getContent());
    }

    private static InterviewMessage message(String role, String content) {
        InterviewMessage message = new InterviewMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
