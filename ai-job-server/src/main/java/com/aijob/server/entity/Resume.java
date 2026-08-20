package com.aijob.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resume")
public class Resume {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String fileName;

    private String filePath;

    private String fileType;

    private Long fileSize;

    private String content;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
