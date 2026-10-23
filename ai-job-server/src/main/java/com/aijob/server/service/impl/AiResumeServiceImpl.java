package com.aijob.server.service.impl;

import com.aijob.server.ai.DeepSeekJsonChat;
import com.aijob.server.ai.ModelJsonParser;
import com.aijob.server.entity.ai.ResumeAnalysisResult;
import com.aijob.server.service.AiResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiResumeServiceImpl implements AiResumeService {

    private static final String SYSTEM = "你是一名专业的IT招聘顾问，负责分析技术岗位求职者简历。请始终以 JSON 对象格式回复。";

    private static final double TEMPERATURE = 0.2;

    private final DeepSeekJsonChat deepSeekJsonChat;

    @Override
    public ResumeAnalysisResult analyze(String content) {
        String text = content == null ? "" : content.strip();
        if (text.isEmpty()) {
            throw new RuntimeException("content 不能为空");
        }
        return deepSeekJsonChat.completeValid(
                SYSTEM,
                prompt(text),
                TEMPERATURE,
                ResumeAnalysisResult.class,
                node -> ModelJsonParser.integralScore(node, "score"),
                "AI返回的简历评分不合法"
        );
    }

    private static String prompt(String text) {
        return """
                请分析下面这份技术简历文本，并严格返回一个 JSON 对象，字段如下：
                {
                  "name": "姓名",
                  "education": {
                    "school": "学校",
                    "major": "专业",
                    "degree": "学历"
                  },
                  "skills": ["技能1", "技能2"],
                  "projects": ["项目1"],
                  "experiences": ["经历1"],
                  "strengths": ["优势1"],
                  "weaknesses": ["不足1"],
                  "suggestions": ["建议1"],
                  "score": 0
                }

                要求：
                1. 只返回合法 JSON，不要 markdown，不要额外说明
                2. 尽量从文本提取真实信息；没有的字段用空字符串或空数组
                3. score 为 0-100 的整数，综合技术栈与经历完整度评分

                简历文本：
                %s
                """.formatted(text);
    }
}
