-- 已有库补列：面试消息记录路由和工具结果，便于对账。历史行保持 NULL。
USE ai_job_platform;

ALTER TABLE interview_message
    ADD COLUMN route_action VARCHAR(20) DEFAULT NULL COMMENT '本轮路由 probe/switch/conclude，仅 AI 消息' AFTER content,
    ADD COLUMN tool_status VARCHAR(20) DEFAULT NULL COMMENT '知识库工具 NONE/SUCCESS/FAILED，仅 AI 消息' AFTER route_action;
