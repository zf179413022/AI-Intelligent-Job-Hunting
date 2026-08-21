package com.aijob.server.service.impl;

import com.aijob.server.entity.KnowledgeChunk;
import com.aijob.server.entity.KnowledgeDocument;
import com.aijob.server.exception.ForbiddenException;
import com.aijob.server.mapper.KnowledgeChunkMapper;
import com.aijob.server.mapper.KnowledgeDocumentMapper;
import com.aijob.server.service.KnowledgeDocumentService;
import com.aijob.server.service.KnowledgeVectorStoreService;
import com.aijob.server.util.DocumentExtractResult;
import com.aijob.server.util.DocumentTextExtractor;
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
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXT = Set.of("pdf", "md");

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeVectorStoreService knowledgeVectorStoreService;

    @Value("${file.knowledge-upload-path:uploads/knowledge}")
    private String knowledgeUploadPath;

    public KnowledgeDocumentServiceImpl(
            KnowledgeDocumentMapper knowledgeDocumentMapper,
            KnowledgeChunkMapper knowledgeChunkMapper,
            KnowledgeVectorStoreService knowledgeVectorStoreService) {
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.knowledgeVectorStoreService = knowledgeVectorStoreService;
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

        String ext = extensionOf(originalFilename);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new RuntimeException("知识库仅支持 PDF / Markdown（.pdf / .md）");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("文件大小不能超过 20MB");
        }

        String fileType = "pdf".equals(ext) ? "PDF" : "MD";
        String storedFileName = UUID.randomUUID() + "." + ext;
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
        document.setFileType(fileType);
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

        // 先删向量（校验 user_id），再删 MySQL chunk / 文件 / 文档
        knowledgeVectorStoreService.deleteByDocument(document.getId(), userId);

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
            DocumentExtractResult result = DocumentTextExtractor.extractDetailed(path, document.getFileType());

            if (result.text() == null || result.text().isBlank()) {
                document.setStatus("FAILED");
                if ("MD".equalsIgnoreCase(document.getFileType())) {
                    document.setErrorMessage("未能从 Markdown 中提取到文本（可能是空文件）");
                } else {
                    document.setErrorMessage("未能从 PDF 中提取到文本（可能是扫描件）");
                }
                document.setPageCount(result.pageCount());
                knowledgeDocumentMapper.updateById(document);
                return knowledgeDocumentMapper.selectById(document.getId());
            }

            document.setStatus("PARSED");
            document.setPageCount(result.pageCount());
            document.setErrorMessage(null);
            knowledgeDocumentMapper.updateById(document);
            return knowledgeDocumentMapper.selectById(document.getId());
        } catch (Exception e) {
            String message = e.getMessage() == null ? "文档解析失败" : e.getMessage();
            if (message.length() > 480) {
                message = message.substring(0, 480);
            }
            document.setStatus("FAILED");
            document.setErrorMessage(message);
            knowledgeDocumentMapper.updateById(document);
            return knowledgeDocumentMapper.selectById(document.getId());
        }
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private KnowledgeDocument requireOwned(Long id, Long userId) {
        KnowledgeDocument document = knowledgeDocumentMapper.selectById(id);
        if (document == null) {
            throw new RuntimeException("知识库文档不存在");
        }
        if (!document.getUserId().equals(userId)) {
            throw new ForbiddenException("无权访问该知识库文档");
        }
        return document;
    }
}
