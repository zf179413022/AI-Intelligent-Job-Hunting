package com.aijob.server.service.impl;

import com.aijob.server.ai.DeepSeekJsonChat;
import com.aijob.server.ai.ModelJsonParser;
import com.aijob.server.entity.ai.JobMatchAiResult;
import com.aijob.server.service.AiJobMatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiJobMatchServiceImpl implements AiJobMatchService {

    private static final String SYSTEM = """
            你是一名专业的IT招聘顾问。只能依据简历中明确写出的信息做判断，禁止虚构经历、技能或证书。请始终只返回合法 JSON 对象，不要使用 markdown。""";

    private static final double TEMPERATURE = 0.2;

    private final DeepSeekJsonChat deepSeekJsonChat;

    @Override
    public JobMatchAiResult match(
            String resumeContent,
            String jobName,
            String companyName,
            String jobDescription) {

        String resume = resumeContent == null ? "" : resumeContent.strip();
        String description = jobDescription == null ? "" : jobDescription.strip();
        if (resume.isEmpty()) {
            throw new RuntimeException("resumeContent 不能为空");
        }
        if (description.isEmpty()) {
            throw new RuntimeException("jobDescription 不能为空");
        }

        String job = jobName == null || jobName.isBlank() ? "未提供" : jobName.strip();
        String company = companyName == null || companyName.isBlank() ? "未提供" : companyName.strip();
        return deepSeekJsonChat.completeValid(
                SYSTEM,
                prompt(job, company, description, resume),
                TEMPERATURE,
                JobMatchAiResult.class,
                node -> ModelJsonParser.integralScore(node, "matchScore"),
                "AI返回的匹配度不合法"
        );
    }

    private static String prompt(String jobName, String companyName, String jobDescription, String resumeContent) {
        return """
                你是一名专业的IT招聘顾问。

                根据候选人的简历和岗位JD进行匹配分析。

                重点分析：
                1. 技能匹配
                2. 项目经验
                3. 技术栈匹配
                4. 岗位要求覆盖程度
                5. 缺失技能
                6. 候选人的优势
                7. 风险
                8. 改进建议

                可靠性约束（必须遵守）：
                1. 只能根据候选人简历中明确存在的信息进行判断。
                2. 不得虚构候选人的工作经历、项目经历、技能或证书。
                3. 如果简历没有明确体现某项技能，应将其视为缺失或未知，放入 missingSkills，不得放入 matchedSkills。
                4. advantages / risks / suggestions / summary 也必须基于简历与JD可观察的事实，禁止编造。

                岗位名称：%s
                公司名称：%s

                岗位JD：
                %s

                候选人简历：
                %s

                严格返回一个 JSON 对象，字段如下：
                {
                  "matchScore": 86,
                  "matchedSkills": ["技能1", "技能2"],
                  "missingSkills": ["缺少技能1"],
                  "advantages": ["优势1"],
                  "risks": ["风险1"],
                  "suggestions": ["建议1"],
                  "summary": "总体评价"
                }

                要求：
                1. 只返回合法 JSON，不要 markdown，不要代码块，不要额外说明
                2. matchScore 为 0-100 的整数
                3. 数组字段没有内容时返回空数组
                """.formatted(jobName, companyName, jobDescription, resumeContent);
    }
}
