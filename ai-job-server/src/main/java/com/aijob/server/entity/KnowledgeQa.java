package com.aijob.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_qa")
public class KnowledgeQa {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String question;

    private String answer;

    /** JSON 数组字符串，如 [1,5,9] */
    private String sourceChunkIds;

    private LocalDateTime createdAt;
}
