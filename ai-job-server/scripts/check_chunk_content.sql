-- 分块内容验收：换行、围栏、HTML 实体、重复转义
--
-- 用法（PowerShell / cmd）：
--   mysql -uroot -proot ai_job_platform < ai-job-server/scripts/check_chunk_content.sql
-- 只看某个文档：把下面的 document_id = 0 改成目标 id
--
-- 判读标准：
--   has_fence     代码块两侧都应为 yes（围栏成对；表格 chunk 为空正常）
--   html_newline  出现 BAD 说明存了 &#xA; 字面量而不是真实换行
--   double_slash  出现 BAD 说明反斜杠被重复转义（如 a \\| b）
--   chars / tail  与原文长度、结尾对照，可发现围栏或内容丢失

SELECT
    id,
    chunk_index,
    CHAR_LENGTH(content) AS chars,
    CASE WHEN content LIKE '%```%' THEN 'yes' ELSE '' END AS has_fence,
    CASE WHEN content LIKE '%&#xA;%' THEN 'BAD' ELSE '' END AS html_newline,
    CASE WHEN content LIKE '%\\\\%' THEN 'BAD' ELSE '' END AS double_slash,
    RIGHT(content, 16) AS tail
FROM knowledge_chunk
WHERE document_id = 0
ORDER BY chunk_index;
