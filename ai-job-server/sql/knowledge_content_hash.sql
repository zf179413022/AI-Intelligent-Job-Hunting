-- 知识库文档：按用户 + 内容哈希防重复上传（仅对新上传生效，历史记录 content_hash 为 NULL）
USE ai_job_platform;

ALTER TABLE knowledge_document
    ADD COLUMN content_hash CHAR(64) DEFAULT NULL
        COMMENT '文件内容 SHA-256（hex）' AFTER file_size;

ALTER TABLE knowledge_document
    ADD UNIQUE KEY uk_user_content_hash (user_id, content_hash);
