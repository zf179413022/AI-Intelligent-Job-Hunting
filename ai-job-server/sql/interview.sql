-- AI 模拟面试（第一阶段：仅建表，未强制外键）
-- 对齐现有风格：snake_case、BIGINT、utf8mb4、不建 MySQL 外键；应用层做 user_id 隔离

CREATE TABLE IF NOT EXISTS interview (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '面试会话ID',
    user_id BIGINT NOT NULL COMMENT '当前登录用户',
    resume_id BIGINT NOT NULL COMMENT '选用的简历ID',

    position VARCHAR(200) NOT NULL COMMENT '面试岗位，如 Java开发工程师',
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING/RUNNING/COMPLETED/CANCELLED',
    score INT DEFAULT NULL COMMENT '面试总分 0-100，结束后回填',

    start_time DATETIME DEFAULT NULL COMMENT '正式开始时间',
    end_time DATETIME DEFAULT NULL COMMENT '结束时间',

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_user_id (user_id),
    INDEX idx_resume_id (resume_id),
    INDEX idx_user_status (user_id, status),
    INDEX idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模拟面试会话';


CREATE TABLE IF NOT EXISTS interview_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '消息ID',
    interview_id BIGINT NOT NULL COMMENT '所属面试会话',

    role VARCHAR(20) NOT NULL COMMENT 'AI 或 USER',
    content TEXT NOT NULL COMMENT '本轮文本内容',

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '消息时间（对齐项目 created_at 命名）',

    INDEX idx_interview_id (interview_id),
    INDEX idx_interview_created (interview_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模拟面试多轮对话';


CREATE TABLE IF NOT EXISTS interview_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '报告ID',
    interview_id BIGINT NOT NULL COMMENT '所属面试会话（一对一）',

    total_score INT DEFAULT NULL COMMENT '综合得分 0-100',
    java_score INT DEFAULT NULL COMMENT 'Java 维度得分',
    mysql_score INT DEFAULT NULL COMMENT 'MySQL 维度得分',
    redis_score INT DEFAULT NULL COMMENT 'Redis 维度得分',
    spring_score INT DEFAULT NULL COMMENT 'Spring 维度得分',

    weak_points TEXT COMMENT '薄弱点 JSON 数组字符串',
    suggestions TEXT COMMENT '改进建议 JSON 数组字符串',
    summary TEXT COMMENT '总体评价',

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_interview_id (interview_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模拟面试报告';
