package com.aijob.server.ai;

import com.aijob.server.entity.ai.InterviewAnswerAiResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Locale;

/**
 * 流式面试的【点评】/【追问】/【继续】标记，规则与原先 Python 解析一致。
 */
public final class InterviewAnswerMarkers {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_EVALUATION = "已记录你的回答。";

    private InterviewAnswerMarkers() {
    }

    public static InterviewAnswerAiResult parse(String text, int userRounds) {
        String raw = text == null ? "" : text.trim();
        InterviewAnswerAiResult result = new InterviewAnswerAiResult();
        result.setEvaluation("");
        result.setNextQuestion("");
        result.setShouldContinue(true);

        if (raw.contains("【点评】")) {
            String after = raw.split("【点评】", 2)[1];
            if (after.contains("【追问】")) {
                String[] evaluationAndRest = after.split("【追问】", 2);
                result.setEvaluation(evaluationAndRest[0].trim());
                String rest = evaluationAndRest[1];
                if (rest.contains("【继续】")) {
                    String[] questionAndFlag = rest.split("【继续】", 2);
                    result.setNextQuestion(questionAndFlag[0].trim());
                    String flag = questionAndFlag[1].trim().toLowerCase(Locale.ROOT);
                    result.setShouldContinue(flag.startsWith("true") || flag.startsWith("是"));
                } else {
                    result.setNextQuestion(rest.trim());
                }
            } else {
                result.setEvaluation(after.trim());
            }
        } else if (!fillFromJson(raw, result)) {
            result.setEvaluation(raw);
        }

        return applyPolicy(result, userRounds);
    }

    /**
     * 同步 JSON 与流式标记共用：满 5 轮强制结束；要继续却没有下一题则失败。
     */
    public static InterviewAnswerAiResult applyPolicy(InterviewAnswerAiResult result, int userRounds) {
        String evaluation = result.getEvaluation() == null ? "" : result.getEvaluation().strip();
        result.setEvaluation(evaluation.isEmpty() ? DEFAULT_EVALUATION : evaluation);

        boolean shouldContinue = result.getShouldContinue() == null || result.getShouldContinue();
        if (userRounds >= 5) {
            shouldContinue = false;
        }

        String nextQuestion = result.getNextQuestion() == null ? "" : result.getNextQuestion().strip();
        if (!shouldContinue) {
            nextQuestion = "";
        }
        result.setShouldContinue(shouldContinue);
        result.setNextQuestion(nextQuestion);

        if (shouldContinue && nextQuestion.isEmpty()) {
            throw new RuntimeException("AI未返回下一题");
        }
        return result;
    }

    private static boolean fillFromJson(String raw, InterviewAnswerAiResult result) {
        try {
            JsonNode node = MAPPER.readTree(ModelJsonParser.stripMarkdownFence(raw));
            if (node == null || !node.isObject()) {
                return false;
            }
            result.setEvaluation(node.path("evaluation").asText(""));
            result.setNextQuestion(node.path("nextQuestion").asText(""));
            JsonNode flag = node.get("shouldContinue");
            if (flag == null || flag.isNull()) {
                result.setShouldContinue(true);
            } else if (flag.isBoolean()) {
                result.setShouldContinue(flag.asBoolean());
            } else {
                String text = flag.asText("").trim().toLowerCase(Locale.ROOT);
                result.setShouldContinue(text.startsWith("true") || text.startsWith("是"));
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
