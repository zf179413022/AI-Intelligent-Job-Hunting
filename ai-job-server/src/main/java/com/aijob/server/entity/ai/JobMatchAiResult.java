package com.aijob.server.entity.ai;

import lombok.Data;

import java.util.List;

/**
 * Python / DeepSeek 岗位匹配返回结构（接口层使用 List，落库前转 JSON 字符串）。
 */
@Data
public class JobMatchAiResult {

    private Integer matchScore;

    private List<String> matchedSkills;

    private List<String> missingSkills;

    private List<String> advantages;

    private List<String> risks;

    private List<String> suggestions;

    private String summary;
}
