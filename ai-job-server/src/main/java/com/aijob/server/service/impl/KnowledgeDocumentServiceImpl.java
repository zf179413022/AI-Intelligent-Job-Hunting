package com.aijob.server.service.impl;

import com.aijob.server.entity.KnowledgeChunk;
import com.aijob.server.entity.KnowledgeDocument;
import com.aijob.server.mapper.KnowledgeChunkMapper;
import com.aijob.server.mapper.KnowledgeDocumentMapper;
import com.aijob.server.service.KnowledgeDocumentService;
import com.aijob.server.util.PdfTextExtractor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;

    @Value("${file.knowledge-upload-path:uploads/knowledge}")
    private String knowledgeUploadPath;

    public KnowledgeDocumentServiceImpl(
            KnowledgeDocumentMapper knowledgeDocumentMapper,
            KnowledgeChunkMapper knowledgeChunkMapper) {
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
    }

    @Override
    @Transactional
    public KnowledgeDocument upload(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new RuntimeException("文件名不能为空");
        }

        String lowerName = originalFilename.toLowerCase();
        if (!lowerName.endsWith(".pdf")) {
            throw new RuntimeException("知识库首版仅支持 PDF 格式");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("文件大小不能超过 20MB");
        }

        String storedFileName = UUID.randomUUID() + ".pdf";
        Path target;
        try {
            Path userDir = Paths.get(knowledgeUploadPath, String.valueOf(userId))
                    .toAbsolutePath()
                    .normalize();
            if (!Files.exists(userDir)) {
                Files.createDirectories(userDir);
            }

            target = userDir.resolve(storedFileName).normalize();
            if (!target.startsWith(userDir)) {
                throw new RuntimeException("非法文件路径");
            }

            file.transferTo(target);
        } catch (IOException e) {
            throw new RuntimeException("知识库文件保存失败", e);
        }

        String title = originalFilename;
        int dot = originalFilename.lastIndexOf('.');
        if (dot > 0) {
            title = originalFilename.substring(0, dot);
        }

        KnowledgeDocument document = new KnowledgeDocument();
        document.setUserId(userId);
        document.setTitle(title);
        document.setFileName(originalFilename);
        document.setFilePath(target.toString());
        document.setFileType("PDF");
        document.setFileSize(file.getSize());
        document.setStatus("UPLOADED");
        document.setChunkCount(null);
        document.setErrorMessage(null);

        knowledgeDocumentMapper.insert(document);

        // 上传后立即尝试解析（不落全文到 knowledge_document）
        return parseInternal(document);
    }

    @Override
    @Transactional
    public KnowledgeDocument parse(Long id, Long userId) {
        KnowledgeDocument document = requireOwned(id, userId);
        return parseInternal(document);
    }

    @Override
    public List<KnowledgeDocument> listByUserId(Long userId) {
        return knowledgeDocumentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getUserId, userId)
                        .orderByDesc(KnowledgeDocument::getCreatedAt)
        );
    }

    @Override
    public KnowledgeDocument getById(Long id, Long userId) {
        return requireOwned(id, userId);
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        KnowledgeDocument document = requireOwned(id, userId);

        knowledgeChunkMapper.delete(
                new LambdaQueryWrapper<KnowledgeChunk>()
                        .eq(KnowledgeChunk::getDocumentId, document.getId())
                        .eq(KnowledgeChunk::getUserId, userId)
        );

        try {
            if (document.getFilePath() != null && !document.getFilePath().isBlank()) {
                Path path = Paths.get(document.getFilePath()).toAbsolutePath().normalize();
                Path root = Paths.get(knowledgeUploadPath).toAbsolutePath().normalize();
                Path userRoot = root.resolve(String.valueOf(userId)).normalize();
                if (path.startsWith(userRoot) && Files.exists(path)) {
                    Files.delete(path);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("知识库文件删除失败", e);
        }

        knowledgeDocumentMapper.deleteById(document.getId());
    }

    private KnowledgeDocument parseInternal(KnowledgeDocument document) {
        try {
            Path path = Paths.get(document.getFilePath());
            PdfTextExtractor.PdfExtractResult result = PdfTextExtractor.extractDetailed(path);

            if (result.text() == null || result.text().isBlank()) {
                document.setStatus("FAILED");
                document.setErrorMessage("未能从 PDF 中提取到文本（可能是扫描件）");
                document.setPageCount(result.pageCount());
                knowledgeDocumentMapper.updateById(document);
                return knowledgeDocumentMapper.selectById(document.getId());
            }

            // 成功：只更新状态与页数，不把全文写入 knowledge_document
            document.setStatus("PARSED");
            document.setPageCount(result.pageCount());
            document.setErrorMessage(null);
            knowledgeDocumentMapper.updateById(document);
            return knowledgeDocumentMapper.selectById(document.getId());
        } catch (Exception e) {
            String message = e.getMessage() == null ? "PDF解析失败" : e.getMessage();
            if (message.length() > 480) {
                message = message.substring(0, 480);
            }
            document.setStatus("FAILED");
            document.setErrorMessage(message);
            knowledgeDocumentMapper.updateById(document);
            return knowledgeDocumentMapper.selectById(document.getId());
        }
    }

    private KnowledgeDocument requireOwned(Long id, Long userId) {
        KnowledgeDocument document = knowledgeDocumentMapper.selectById(id);
        if (document == null) {
            throw new RuntimeException("知识库文档不存在");
        }
        if (!document.getUserId().equals(userId)) {
            throw new RuntimeException("无权访问该知识库文档");
        }
        return document;
    }
}
