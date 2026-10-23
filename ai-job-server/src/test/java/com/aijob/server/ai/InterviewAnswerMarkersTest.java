package com.aijob.server.ai;

import com.aijob.server.entity.ai.InterviewAnswerAiResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewAnswerMarkersTest {

    @Test
    void parsesMarkers() {
        String text = """
                【点评】
                回答提到了索引，但没说明回表。
                【追问】
                聚簇索引和二级索引有什么区别？
                【继续】
                true
                """;

        InterviewAnswerAiResult result = InterviewAnswerMarkers.parse(text, 1);

        assertEquals("回答提到了索引，但没说明回表。", result.getEvaluation());
        assertEquals("聚簇索引和二级索引有什么区别？", result.getNextQuestion());
        assertTrue(result.getShouldContinue());
    }

    @Test
    void stopsWhenFlagIsFalse() {
        String text = """
                【点评】
                可以结束了。
                【追问】
                还想再问一题
                【继续】
                false
                """;

        InterviewAnswerAiResult result = InterviewAnswerMarkers.parse(text, 1);

        assertFalse(result.getShouldContinue());
        assertEquals("", result.getNextQuestion());
    }

    @Test
    void stopsAfterFiveUserRounds() {
        String text = """
                【点评】
                继续也该收束。
                【追问】
                再问 Redis？
                【继续】
                true
                """;

        InterviewAnswerAiResult result = InterviewAnswerMarkers.parse(text, 5);

        assertFalse(result.getShouldContinue());
        assertEquals("", result.getNextQuestion());
    }

    @Test
    void fallsBackToFencedJson() {
        String text = """
                ```json
                {"evaluation":"方向对","nextQuestion":"事务隔离级别有哪些？","shouldContinue":true}
                ```
                """;

        InterviewAnswerAiResult result = InterviewAnswerMarkers.parse(text, 0);

        assertEquals("方向对", result.getEvaluation());
        assertEquals("事务隔离级别有哪些？", result.getNextQuestion());
        assertTrue(result.getShouldContinue());
    }

    @Test
    void rejectsPlainTextWithoutNextQuestion() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> InterviewAnswerMarkers.parse("先记住这个回答。", 0));
        assertEquals("AI未返回下一题", ex.getMessage());
    }

    @Test
    void rejectsContinueWithoutNextQuestion() {
        String text = """
                【点评】
                还需要追问。
                【追问】
                【继续】
                true
                """;

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> InterviewAnswerMarkers.parse(text, 1));
        assertEquals("AI未返回下一题", ex.getMessage());
    }
}
