package com.aijob.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("job_match")
public class JobMatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long resumeId;

    private String jobName;

    private String companyName;

    private String jobDescription;

    private Integer matchScore;

    /** JSON 数组字符串 */
    private String matchedSkills;

    /** JSON 数组字符串 */
    private String missingSkills;

    /** JSON 数组字符串 */
    private String advantages;

    /** JSON 数组字符串 */
    private String risks;

    /** JSON 数组字符串 */
    private String suggestions;

    private String summary;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
