-- ============================================================================
-- CareerPilot · Agent 配置中心 + 用户行为视图
--
-- 本迁移只做两件事：
--   1. 给 t_platform_config 补齐 Agent 项目真正会用到的配置项
--      （LLM / 向量 / TTS / ASR / Agent 编排 / 筛选合规策略）
--   2. 不新增业务表 —— 用户行为直接用 t_user / t_resume / t_jd /
--      t_match_record / t_ai_task / t_hr_insight 聚合，
--      管理端读到的就是业务库真实发生的事，不存在「日志表和业务表对不上」
--
-- 幂等：重复执行不会覆盖运营已经改过的配置值，只会补缺失行 / 补描述。
-- ============================================================================

INSERT INTO `t_platform_config` (`config_key`, `config_value`, `description`)
VALUES
    ('llm.provider',
     JSON_OBJECT(
        'enabled', FALSE,
        'provider', 'openai-compatible',
        'baseUrl', 'https://api.openai.com/v1',
        'model', 'gpt-4o',
        'temperature', 0.3,
        'maxTokens', 2048
     ),
     '大模型接入配置；enabled=false 时全部走确定性规则引擎'),

    ('embedding.provider',
     JSON_OBJECT(
        'enabled', FALSE,
        'provider', 'local',
        'baseUrl', '',
        'model', 'text-embedding-3-small',
        'dimension', 1536,
        'note', '用于 JD / 简历语义匹配；关闭时使用关键词与规则打分'
     ),
     '向量模型配置（语义匹配 / 人才画像检索）'),

    ('tts.provider',
     JSON_OBJECT(
        'enabled', FALSE,
        'provider', 'openai',
        'baseUrl', '',
        'model', 'tts-1',
        'voice', 'alloy',
        'speed', 1.0,
        'note', '模拟面试语音播报；关闭时使用浏览器语音合成'
     ),
     '语音合成（TTS）配置；密钥只走部署环境变量'),

    ('asr.provider',
     JSON_OBJECT(
        'enabled', FALSE,
        'provider', 'openai',
        'baseUrl', '',
        'model', 'whisper-1',
        'language', 'zh',
        'note', '模拟面试语音回答转写；关闭时只接受文字回答'
     ),
     '语音识别（ASR）配置；密钥只走部署环境变量'),

    ('agent.registry',
     JSON_OBJECT(
        'orchestrator', JSON_OBJECT(
            'enabled', TRUE,
            'port', 8001,
            'endpoint', 'http://localhost:8001',
            'timeoutSeconds', 120
        ),
        'agents', JSON_ARRAY(
            JSON_OBJECT('key', 'jd-parser', 'name', 'JD 解析 Agent', 'probeName', 'jd-parser-agent',
                        'port', 8002, 'enabled', TRUE, 'model', 'rule-engine', 'timeoutSeconds', 60,
                        'description', '抽取岗位技能、经验与硬性条件'),
            JSON_OBJECT('key', 'resume-analyzer', 'name', '简历分析 Agent', 'probeName', 'resume-analyzer-agent',
                        'port', 8003, 'enabled', TRUE, 'model', 'rule-engine', 'timeoutSeconds', 60,
                        'description', 'ATS 多维评分与改进建议'),
            JSON_OBJECT('key', 'match', 'name', '匹配打分 Agent', 'probeName', 'match-agent',
                        'port', 8004, 'enabled', TRUE, 'model', 'rule-engine', 'timeoutSeconds', 60,
                        'description', '语义 / 关键词 / 业务三段式匹配'),
            JSON_OBJECT('key', 'resume-optimizer', 'name', '简历优化 Agent', 'probeName', 'resume-optimizer-agent',
                        'port', 8005, 'enabled', TRUE, 'model', 'rule-engine', 'timeoutSeconds', 90,
                        'description', '按 JD 重写经历，补足 STAR 结构'),
            JSON_OBJECT('key', 'interview', 'name', '模拟面试 Agent', 'probeName', 'interview-agent',
                        'port', 8006, 'enabled', TRUE, 'model', 'rule-engine', 'timeoutSeconds', 90,
                        'description', '多轮追问、评分与 STAR 覆盖判定'),
            JSON_OBJECT('key', 'cover-letter', 'name', '求职信 Agent', 'probeName', 'cover-letter-agent',
                        'port', 8007, 'enabled', TRUE, 'model', 'rule-engine', 'timeoutSeconds', 60,
                        'description', '按岗位与语气生成求职信'),
            JSON_OBJECT('key', 'career-advisor', 'name', '职业规划 Agent', 'probeName', 'career-advisor-agent',
                        'port', 8008, 'enabled', TRUE, 'model', 'rule-engine', 'timeoutSeconds', 90,
                        'description', '分阶段路线图与技能缺口')
        )
     ),
     'Agent 编排配置：开关、端口、超时与模型标识（不存密钥）'),

    ('screening.policy',
     JSON_OBJECT(
        'requireConsent', TRUE,
        'retainDays', 180,
        'autoReject', FALSE,
        'allowCrossUserSearch', FALSE,
        'note', 'HR 只能分析自己导入的候选人简历；平台不做投递与站内沟通'
     ),
     '招聘侧合规策略；autoReject 与跨用户检索默认关闭')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

-- 行为视图的常用过滤条件加索引；IF NOT EXISTS 语法在 MySQL 8 不支持，
-- 因此用存储过程判重，避免重复启动脚本报错。
DROP PROCEDURE IF EXISTS `cp_add_index_if_missing`;
DELIMITER $$
CREATE PROCEDURE `cp_add_index_if_missing`(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_columns VARCHAR(255)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND index_name = p_index
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD INDEX `', p_index, '` (', p_columns, ')');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL `cp_add_index_if_missing`('t_ai_task', 'idx_task_type_created', '`task_type`, `created_at`');
CALL `cp_add_index_if_missing`('t_hr_insight', 'idx_hr_insight_user_created', '`user_id`, `created_at`');
DROP PROCEDURE IF EXISTS `cp_add_index_if_missing`;
