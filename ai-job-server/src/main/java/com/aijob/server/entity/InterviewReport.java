package com.aijob.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_report")
public class InterviewReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long interviewId;

    private Integer totalScore;

    private Integer javaScore;

    private Integer mysqlScore;

    private Integer redisScore;

    private Integer springScore;

    /** JSON 数组字符串 */
    private String weakPoints;

    /** JSON 数组字符串 */
    private String suggestions;

    private String summary;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
