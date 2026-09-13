package com.aijob.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_document")
public class KnowledgeDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String fileName;

    private String filePath;

    private String fileType;

    private Long fileSize;

    /**
     * UPLOADED / PARSED / CHUNKED / EMBEDDED / READY / FAILED
     */
    private String status;

    private Integer pageCount;

    private Integer chunkCount;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
