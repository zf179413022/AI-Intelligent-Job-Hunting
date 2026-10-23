package com.aijob.server.controller;

import com.aijob.server.entity.KnowledgeChunk;
import com.aijob.server.entity.KnowledgeDocument;
import com.aijob.server.entity.User;
import com.aijob.server.mapper.UserMapper;
import com.aijob.server.security.LoginUserUtil;
import com.aijob.server.service.KnowledgeDocumentService;
import com.aijob.server.service.KnowledgeIngestService;
import com.aijob.server.vo.KnowledgeIngestVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge/documents")
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeIngestService knowledgeIngestService;
    private final LoginUserUtil loginUserUtil;
    private final UserMapper userMapper;

    public KnowledgeDocumentController(
            KnowledgeDocumentService knowledgeDocumentService,
            KnowledgeIngestService knowledgeIngestService,
            LoginUserUtil loginUserUtil,
            UserMapper userMapper) {
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.knowledgeIngestService = knowledgeIngestService;
        this.loginUserUtil = loginUserUtil;
        this.userMapper = userMapper;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KnowledgeDocument upload(@RequestParam("file") MultipartFile file) {
        return knowledgeDocumentService.upload(file, currentUser().getId());
    }

    @GetMapping
    public List<KnowledgeDocument> list() {
        return knowledgeDocumentService.listByUserId(currentUser().getId());
    }

    @GetMapping("/{id}")
    public KnowledgeDocument detail(@PathVariable Long id) {
        return knowledgeDocumentService.getById(id, currentUser().getId());
    }

    @PostMapping("/{id}/parse")
    public KnowledgeDocument parse(@PathVariable Long id) {
        return knowledgeDocumentService.parse(id, currentUser().getId());
    }

    /**
     * RAG 5.4/5.5：切分 + Embedding + 写入 Chroma → READY
     */
    @PostMapping("/{id}/ingest")
    public KnowledgeIngestVO ingest(@PathVariable Long id) {
        return knowledgeIngestService.chunkAndEmbed(id, currentUser().getId());
    }

    @GetMapping("/{id}/chunks")
    public List<KnowledgeChunk> chunks(@PathVariable Long id) {
        return knowledgeIngestService.listChunks(id, currentUser().getId());
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        knowledgeDocumentService.delete(id, currentUser().getId());
        return "删除成功";
    }

    private User currentUser() {
        String username = loginUserUtil.getUsername();
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
        );
        if (user == null) {
            throw new RuntimeException("当前用户不存在");
        }
        return user;
    }
}
