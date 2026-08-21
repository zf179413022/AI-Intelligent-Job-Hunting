-- RAG 知识库（5.1：仅建表）
-- 库：ai_job_platform
-- 对齐现有风格：snake_case、BIGINT、utf8mb4、不建 MySQL 外键；应用层做 user_id 隔离
-- 不修改：user / resume / resume_ai_analysis / job_match / interview*

-- ---------------------------------------------------------------------------
-- 知识库文档（上传 + 解析/入库状态机）
-- status: UPLOADED → PARSED → CHUNKED → EMBEDDED → READY
--         任意步骤失败 → FAILED
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文档ID',
    user_id BIGINT NOT NULL COMMENT '所属用户（数据隔离）',

    title VARCHAR(200) NOT NULL COMMENT '展示标题，默认可取文件名',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_path VARCHAR(500) NOT NULL COMMENT '本地路径，如 uploads/knowledge/{userId}/xxx.pdf',
    file_type VARCHAR(20) NOT NULL DEFAULT 'PDF' COMMENT '首版仅 PDF',
    file_size BIGINT DEFAULT NULL COMMENT '文件大小（字节）',

    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED' COMMENT 'UPLOADED/PARSED/CHUNKED/EMBEDDED/READY/FAILED',
    page_count INT DEFAULT NULL COMMENT '页数（可选）',
    chunk_count INT DEFAULT NULL COMMENT '分块数量（入库后回填）',
    error_message VARCHAR(500) DEFAULT NULL COMMENT '失败原因',

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_user_id (user_id),
    INDEX idx_user_status (user_id, status),
    INDEX idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG知识库文档';


-- ---------------------------------------------------------------------------
-- 知识分块元数据（文本在 MySQL；向量在 Chroma，通过 vector_id / chunk.id 关联）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '分块ID（亦作为向量 metadata.chunk_id）',
    document_id BIGINT NOT NULL COMMENT '所属文档',
    user_id BIGINT NOT NULL COMMENT '冗余用户ID，便于按用户删除/过滤',

    chunk_index INT NOT NULL COMMENT '文档内序号，从 0 开始',
    content TEXT NOT NULL COMMENT '分块原文',
    token_estimate INT DEFAULT NULL COMMENT '预估 token/字数（可选）',
    vector_id VARCHAR(64) DEFAULT NULL COMMENT 'Chroma 内向量ID（可选）',

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_document_chunk (document_id, chunk_index),
    INDEX idx_document_id (document_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG知识分块（文本元数据）';


-- ---------------------------------------------------------------------------
-- 知识库问答记录（答案 + 引用来源 chunk id 列表）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS knowledge_qa (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '问答ID',
    user_id BIGINT NOT NULL COMMENT '提问用户',

    question TEXT NOT NULL COMMENT '用户问题',
    answer TEXT COMMENT '模型回答',
    source_chunk_ids TEXT COMMENT '引用的 chunk id JSON 数组，如 [1,5,9]',

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_user_id (user_id),
    INDEX idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG知识库问答历史';
