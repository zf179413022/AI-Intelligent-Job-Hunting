package com.aijob.server.service.impl;

import com.aijob.server.ai.DeepSeekJsonChat;
import com.aijob.server.entity.InterviewMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredScoreValidationTest {

    @Test
    void reportRejectsNullNegativeAndOutOfRangeScores() {
        assertRejectedReport("null");
        assertRejectedReport("-1");
        assertRejectedReport("150");
    }

    @Test
    void reportAcceptsTheRetryWhenTheSecondPayloadIsValid() {
        SequenceModel model = new SequenceModel(List.of(report("150"), report("80")));
        var result = interview(model).report("熟悉 Java", "Java 后端", List.of(message()));

        assertEquals(80, result.getTotalScore());
        assertEquals(2, model.calls);
    }

    @Test
    void resumeRejectsIllegalScoresAndDoesNotReturnAClampedValue() {
        assertRejectedResume("null");
        assertRejectedResume("-1");
        assertRejectedResume("150");
    }

    @Test
    void resumeAcceptsTheRetryWhenTheSecondScoreIsValid() {
        SequenceModel model = new SequenceModel(List.of(resume("150"), resume("76")));
        var result = new AiResumeServiceImpl(chat(model)).analyze("熟悉 Java");

        assertEquals(76, result.getScore());
        assertEquals(2, model.calls);
    }

    @Test
    void jobMatchRejectsIllegalScores() {
        assertRejectedMatch("null");
        assertRejectedMatch("-1");
        assertRejectedMatch("150");
    }

    @Test
    void jobMatchAcceptsTheRetryWhenTheSecondScoreIsValid() {
        SequenceModel model = new SequenceModel(List.of(match("150"), match("86")));
        var result = new AiJobMatchServiceImpl(chat(model)).match("熟悉 Java", "后端", "某公司", "要求 Java");

        assertEquals(86, result.getMatchScore());
        assertEquals(2, model.calls);
    }

    private static void assertRejectedReport(String score) {
        SequenceModel model = new SequenceModel(List.of(report(score), report(score)));
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> interview(model).report("熟悉 Java", "Java 后端", List.of(message())));
        assertTrue(error.getMessage().contains("不合法"));
        assertEquals(2, model.calls);
    }

    private static void assertRejectedResume(String score) {
        SequenceModel model = new SequenceModel(List.of(resume(score), resume(score)));
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> new AiResumeServiceImpl(chat(model)).analyze("熟悉 Java"));
        assertTrue(error.getMessage().contains("不合法"));
        assertEquals(2, model.calls);
    }

    private static void assertRejectedMatch(String score) {
        SequenceModel model = new SequenceModel(List.of(match(score), match(score)));
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> new AiJobMatchServiceImpl(chat(model)).match("熟悉 Java", "后端", "某公司", "要求 Java"));
        assertTrue(error.getMessage().contains("不合法"));
        assertEquals(2, model.calls);
    }

    private static AiInterviewServiceImpl interview(SequenceModel model) {
        return new AiInterviewServiceImpl(chat(model), null);
    }

    private static DeepSeekJsonChat chat(SequenceModel model) {
        return new DeepSeekJsonChat(model, new ObjectMapper());
    }

    private static InterviewMessage message() {
        InterviewMessage message = new InterviewMessage();
        message.setRole("USER");
        message.setContent("回答");
        return message;
    }

    private static String report(String score) {
        return """
                {"totalScore":%s,"javaScore":80,"mysqlScore":70,"redisScore":60,"springScore":75,"weakPoints":[],"suggestions":[],"summary":"总体还可以"}
                """.formatted(score);
    }

    private static String resume(String score) {
        return """
                {"name":"张三","skills":["Java"],"score":%s,"suggestions":["补充项目"]}
                """.formatted(score);
    }

    private static String match(String score) {
        return """
                {"matchScore":%s,"matchedSkills":["Java"],"missingSkills":[],"advantages":[],"risks":[],"suggestions":[],"summary":"匹配"}
                """.formatted(score);
    }

    private static final class SequenceModel implements ChatModel {
        private final List<String> replies;
        private int index;
        private int calls;

        private SequenceModel(List<String> replies) {
            this.replies = replies;
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            calls++;
            int cursor = Math.min(index, replies.size() - 1);
            index++;
            return ChatResponse.builder().aiMessage(AiMessage.from(replies.get(cursor))).build();
        }
    }
}
