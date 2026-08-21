package com.aijob.server.entity.ai;

import lombok.Data;

import java.util.List;

@Data
public class InterviewReportAiResult {

    private Integer totalScore;

    private Integer javaScore;

    private Integer mysqlScore;

    private Integer redisScore;

    private Integer springScore;

    private List<String> weakPoints;

    private List<String> suggestions;

    private String summary;
}
