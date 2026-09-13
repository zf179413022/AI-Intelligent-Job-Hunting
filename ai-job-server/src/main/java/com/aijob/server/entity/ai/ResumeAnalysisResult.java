package com.aijob.server.entity.ai;

import lombok.Data;

import java.util.List;

@Data
public class ResumeAnalysisResult {

    private String name;

    private List<String> skills;

    private Integer score;

    private List<String> suggestions;
}
