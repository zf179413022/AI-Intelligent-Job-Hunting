package com.aijob.server.ai;

import com.aijob.server.entity.ai.ResumeAnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelJsonParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsPlainJson() {
        ResumeAnalysisResult result = ModelJsonParser.read(
                objectMapper,
                "{\"name\":\"Ada\",\"skills\":[\"Java\"],\"score\":80,\"suggestions\":[\"补项目\"]}",
                ResumeAnalysisResult.class
        );

        assertEquals("Ada", result.getName());
        assertEquals(80, result.getScore());
        assertEquals("Java", result.getSkills().getFirst());
    }

    @Test
    void stripsJsonFence() {
        String raw = """
                ```json
                {"name":"Ada","score":70}
                ```
                """;

        ResumeAnalysisResult result = ModelJsonParser.read(objectMapper, raw, ResumeAnalysisResult.class);

        assertEquals("Ada", result.getName());
        assertEquals(70, result.getScore());
    }

    @Test
    void stripsPlainFence() {
        String raw = """
                ```
                {"name":"Grace","score":90}
                ```
                """;

        assertEquals("Grace", ModelJsonParser.read(objectMapper, raw, ResumeAnalysisResult.class).getName());
    }

    @Test
    void rejectsNonJson() {
        assertThrows(RuntimeException.class,
                () -> ModelJsonParser.read(objectMapper, "不是 JSON", ResumeAnalysisResult.class));
    }
}
