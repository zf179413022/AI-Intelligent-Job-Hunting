package com.aijob.server.service;

import com.aijob.server.entity.KnowledgeDocument;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KnowledgeDocumentService {

    /**
     * 上传 PDF 并尝试解析：成功 → PARSED，失败 → FAILED（不落全文）。
     */
    KnowledgeDocument upload(MultipartFile file, Long userId);

    /**
     * 对已上传文档重新解析（可用于 FAILED 重试）。
     */
    KnowledgeDocument parse(Long id, Long userId);

    List<KnowledgeDocument> listByUserId(Long userId);

    KnowledgeDocument getById(Long id, Long userId);

    void delete(Long id, Long userId);
}
