CREATE TABLE IF NOT EXISTS job_match (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '当前登录用户',
    resume_id BIGINT NOT NULL COMMENT '用户简历ID',

    job_name VARCHAR(200) DEFAULT NULL COMMENT '岗位名称',
    company_name VARCHAR(200) DEFAULT NULL COMMENT '公司名称',
    job_description TEXT COMMENT '岗位JD',

    match_score INT DEFAULT NULL COMMENT '匹配度 0-100',

    matched_skills TEXT COMMENT '已匹配技能 JSON数组字符串',
    missing_skills TEXT COMMENT '缺少技能 JSON数组字符串',
    advantages TEXT COMMENT '优势 JSON数组字符串',
    risks TEXT COMMENT '风险 JSON数组字符串',
    suggestions TEXT COMMENT '建议 JSON数组字符串',
    summary TEXT COMMENT '总体评价',

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_user_id (user_id),
    INDEX idx_resume_id (resume_id),
    INDEX idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI岗位匹配记录';
