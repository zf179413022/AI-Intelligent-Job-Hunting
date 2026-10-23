package com.aijob.server.entity.ai;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resume_ai_analysis")
public class ResumeAiAnalysis {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long resumeId;

    private Long userId;

    private String name;

    private String skills;

    private Integer score;

    private String suggestions;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
