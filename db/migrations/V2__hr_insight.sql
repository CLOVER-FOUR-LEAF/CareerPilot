-- ============================================================================
-- CareerPilot · HR 侧能力扩展
--
-- 对应计划书 §6.1。刻意不修改 V1__init_schema.sql ——
-- 迁移文件是「唯一事实来源」的单向演进，V1 保持原样。
--
-- 两张新表：
--   t_hr_insight          HR 洞察报告（gap → 验证问题映射）
--   t_verification_record 面试追问记录（人工决策留痕，合规要求 C-6）
--
-- 另有 t_agent_invocation 增列，给 LLM 调用补上可观测性（计划书 §5.3 要求 5）：
--   prompt_version  用的哪版 prompt
--   input_tokens    输入 token 数
--   output_tokens   输出 token 数
--   evidence_failed 引用校验失败次数（幻觉率的直接度量）
-- ============================================================================

-- ── HR 洞察报告 ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `t_hr_insight` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `insight_id`      VARCHAR(32)  NOT NULL COMMENT '业务ID ins_xxx',
    `task_id`         VARCHAR(32)  DEFAULT NULL COMMENT '关联 t_ai_task.task_id',
    `user_id`         VARCHAR(32)  NOT NULL,
    `jd_id`           VARCHAR(32)  DEFAULT NULL COMMENT 't_jd.jd_id（业务ID）',
    `resume_id`       VARCHAR(32)  DEFAULT NULL COMMENT 't_resume.resume_id（业务ID）',
    `mode`            VARCHAR(16)  NOT NULL DEFAULT 'RECRUITER' COMMENT 'RECRUITER/HR_VIEW',
    `capability_json` JSON         DEFAULT NULL COMMENT '能力维度模型',
    `gap_plan_json`   JSON         DEFAULT NULL COMMENT 'gap → 验证问题映射',
    `report_json`     JSON         DEFAULT NULL COMMENT '完整报告',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_insight_id` (`insight_id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_task` (`task_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'HR 侧洞察报告';

-- ── 面试追问记录 ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `t_verification_record` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `record_id`     VARCHAR(32)  NOT NULL COMMENT '业务ID ver_xxx',
    `insight_id`    VARCHAR(32)  NOT NULL,
    `vq_id`         VARCHAR(32)  NOT NULL COMMENT 'VerificationQuestion.id',
    `round_no`      INT          NOT NULL DEFAULT 1,
    `question`      TEXT         DEFAULT NULL,
    `answer`        MEDIUMTEXT   DEFAULT NULL,
    `coverage`      VARCHAR(24)  DEFAULT NULL COMMENT 'demonstrated/adjacent/not_evidenced',
    `confidence`    DECIMAL(4,3) DEFAULT NULL,
    `reason`        TEXT         DEFAULT NULL,
    `evidence_ref`  TEXT         DEFAULT NULL,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_record_id` (`record_id`),
    KEY `idx_insight` (`insight_id`),
    KEY `idx_vq` (`vq_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '验证追问记录';

-- ── LLM 可观测性增列 ─────────────────────────────────────────────
-- 注意：model_name 列已存在（VARCHAR 64），直接复用，不要重复添加。
ALTER TABLE `t_agent_invocation`
    ADD COLUMN `prompt_version`  VARCHAR(32) DEFAULT NULL COMMENT 'prompt 版本',
    ADD COLUMN `input_tokens`    INT         DEFAULT NULL COMMENT '输入 token 数',
    ADD COLUMN `output_tokens`   INT         DEFAULT NULL COMMENT '输出 token 数',
    ADD COLUMN `evidence_failed` INT         DEFAULT NULL COMMENT '引用校验失败次数';
