package com.aijob.server.service.impl;

import com.aijob.server.exception.ForbiddenException;

import com.aijob.server.entity.Resume;
import com.aijob.server.mapper.ResumeMapper;
import com.aijob.server.service.ResumeService;
import com.aijob.server.util.PdfTextExtractor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
public class ResumeServiceImpl implements ResumeService {

    private final ResumeMapper resumeMapper;

    @Value("${file.upload-path}")
    private String uploadPath;

    public ResumeServiceImpl(ResumeMapper resumeMapper) {
        this.resumeMapper = resumeMapper;
    }

    @Override
    public Resume upload(MultipartFile file, Long userId) {

        if (file == null || file.isEmpty()) {
            throw new RuntimeException("上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new RuntimeException("文件名不能为空");
        }

        String lowerName = originalFilename.toLowerCase();
        if (!lowerName.endsWith(".pdf") && !lowerName.endsWith(".docx")) {
            throw new RuntimeException("仅支持 PDF 或 DOCX 格式");
        }

        String fileType = lowerName.endsWith(".pdf") ? ".pdf" : ".docx";
        String storedFileName = UUID.randomUUID() + fileType;

        try {
            Path dir = Paths.get(uploadPath);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            Path target = dir.resolve(storedFileName).toAbsolutePath().normalize();
            file.transferTo(target);

            Resume resume = new Resume();
            resume.setUserId(userId);
            resume.setFileName(originalFilename);
            resume.setFilePath(target.toString());
            resume.setFileType(fileType);
            resume.setFileSize(file.getSize());
            resume.setStatus("UPLOADED");

            resumeMapper.insert(resume);
            return resume;
        } catch (IOException e) {
            throw new RuntimeException("简历文件保存失败", e);
        }
    }

    @Override
    public List<Resume> listByUserId(Long userId) {

        return resumeMapper.selectList(
                new LambdaQueryWrapper<Resume>()
                        .eq(Resume::getUserId, userId)
                        .orderByDesc(Resume::getCreatedAt)
        );
    }

    @Override
    public Resume getById(Long id) {
        return resumeMapper.selectById(id);
    }

    @Override
    public void delete(Long id, Long userId) {

        Resume resume = resumeMapper.selectById(id);

        if (resume == null) {
            throw new RuntimeException("简历不存在");
        }

        // 数据权限校验
        if (!resume.getUserId().equals(userId)) {
            throw new ForbiddenException("无权删除该简历");
        }

        try {
            Path path = Paths.get(resume.getFilePath());

            if (Files.exists(path)) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new RuntimeException("简历文件删除失败", e);
        }

        resumeMapper.deleteById(id);
    }

    @Override
    public Resume parse(Long id, Long userId) {

        Resume resume = resumeMapper.selectById(id);

        if (resume == null) {
            throw new RuntimeException("简历不存在");
        }

        // 数据权限校验
        if (!resume.getUserId().equals(userId)) {
            throw new ForbiddenException("无权解析该简历");
        }

        if (!".pdf".equalsIgnoreCase(resume.getFileType())) {
            throw new RuntimeException("目前只支持解析 PDF 简历");
        }

        resume.setStatus("PARSING");
        resumeMapper.updateById(resume);

        try {

            Path path = Paths.get(resume.getFilePath());

            String text = PdfTextExtractor.extract(path);

            if (text.isBlank()) {
                throw new RuntimeException("未能从 PDF 中提取到文本");
            }

            resume.setContent(text);
            resume.setStatus("PARSED");

            resumeMapper.updateById(resume);

            return resume;

        } catch (Exception e) {

            resume.setStatus("PARSE_FAILED");
            resumeMapper.updateById(resume);

            throw new RuntimeException("简历解析失败", e);
        }
    }
}
