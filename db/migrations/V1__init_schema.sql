-- ============================================================================
-- CareerPilot 初始化 Schema
--
-- 这是全系统的唯一事实来源。所有 Java 实体（@TableName / @TableField）、
-- MyBatis 查询与 Python Agent 的回调写回，都必须与本文件严格对齐。
--
-- 约定：
--   * 每张主表 = 自增 id（REST 路径参数用） + 业务 ID xxx_id（跨服务用）
--   * 枚举型状态统一用 VARCHAR + CHECK 友好的取值，见各列注释
--   * JSON 列存 Agent 的原始输出，前端直接消费，不再二次包装
-- ============================================================================


-- ============================================================================
-- 1. 用户相关
-- ============================================================================

CREATE TABLE IF NOT EXISTS `t_user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键，REST 路径参数用',
    `user_id`       VARCHAR(32)  NOT NULL COMMENT '业务ID usr_xxx，跨服务用',
    `email`         VARCHAR(128)          DEFAULT NULL COMMENT '邮箱',
    `phone`         VARCHAR(20)           DEFAULT NULL COMMENT '手机号',
    `password_hash` VARCHAR(120) NOT NULL COMMENT 'BCrypt 密码哈希',
    `nickname`      VARCHAR(64)           DEFAULT NULL COMMENT '昵称',
    `avatar_url`    VARCHAR(256)          DEFAULT NULL COMMENT '头像URL',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1=正常 0=封禁',
    `last_login_at` DATETIME              DEFAULT NULL COMMENT '最后登录时间',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`),
    UNIQUE KEY `uk_email` (`email`),
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户表';


CREATE TABLE IF NOT EXISTS `t_user_profile` (
    `id`                    BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`               VARCHAR(32) NOT NULL COMMENT '业务ID',
    `gender`                VARCHAR(8)   DEFAULT NULL,
    `birth_year`            INT          DEFAULT NULL,
    `city`                  VARCHAR(32)  DEFAULT NULL COMMENT '所在城市',
    `current_title`         VARCHAR(64)  DEFAULT NULL COMMENT '当前职位',
    `years_of_experience`   INT          DEFAULT NULL COMMENT '工作年限',
    `education_level`       VARCHAR(16)  DEFAULT NULL COMMENT '学历',
    `school`                VARCHAR(64)  DEFAULT NULL,
    `major`                 VARCHAR(64)  DEFAULT NULL,
    `graduation_year`       INT          DEFAULT NULL,
    `target_industry`       VARCHAR(32)  DEFAULT NULL,
    `target_position`       VARCHAR(64)  DEFAULT NULL,
    `target_seniority`      VARCHAR(16)  DEFAULT NULL,
    `target_city_json`      JSON         DEFAULT NULL COMMENT '目标城市数组',
    `target_salary_min`     INT          DEFAULT NULL COMMENT '期望最低薪资(k)',
    `target_salary_max`     INT          DEFAULT NULL COMMENT '期望最高薪资(k)',
    `skills_json`           JSON         DEFAULT NULL COMMENT '技能标签数组',
    `preferences_json`      JSON         DEFAULT NULL COMMENT '偏好设置',
    `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户画像';


CREATE TABLE IF NOT EXISTS `t_membership` (
    `id`                  BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`             VARCHAR(32) NOT NULL,
    `level`               TINYINT     NOT NULL DEFAULT 0 COMMENT '0=免费 1=Pro 2=企业版',
    `expire_at`           DATETIME             DEFAULT NULL COMMENT '到期时间',
    `ai_quota_remaining`  INT         NOT NULL DEFAULT 3 COMMENT 'AI 调用剩余次数',
    `ai_quota_total`      INT         NOT NULL DEFAULT 3 COMMENT 'AI 调用总额度',
    `auto_renew`          TINYINT     NOT NULL DEFAULT 0,
    `created_at`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '会员权益';


-- ============================================================================
-- 2. 简历
-- ============================================================================

CREATE TABLE IF NOT EXISTS `t_resume` (
    `id`                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键，REST 路径参数用',
    `resume_id`          VARCHAR(32) NOT NULL COMMENT '业务ID res_xxx',
    `user_id`            VARCHAR(32) NOT NULL,
    `title`              VARCHAR(128)         DEFAULT NULL COMMENT '简历标题',
    `current_version_id` VARCHAR(32)          DEFAULT NULL COMMENT '当前激活版本',
    `ats_score`          INT                  DEFAULT 0 COMMENT '最新 ATS 评分 0-100',
    `parse_status`       VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/SUCCESS/FAILED',
    `file_id`            VARCHAR(32)          DEFAULT NULL COMMENT '关联 t_file.file_id',
    `is_primary`         TINYINT     NOT NULL DEFAULT 0,
    `is_deleted`         TINYINT     NOT NULL DEFAULT 0,
    `created_at`         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_resume_id` (`resume_id`),
    KEY `idx_user_status` (`user_id`, `is_deleted`),
    KEY `idx_updated_at` (`updated_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '简历主表';


CREATE TABLE IF NOT EXISTS `t_resume_version` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `version_id`    VARCHAR(32)  NOT NULL COMMENT '业务ID ver_xxx',
    `resume_id`     VARCHAR(32)  NOT NULL COMMENT '简历业务ID',
    `version_no`    INT          NOT NULL COMMENT '版本号',
    `parsed_text`   MEDIUMTEXT            DEFAULT NULL COMMENT '从文件抽取出的纯文本，供 Agent 复用',
    `analysis_json` JSON                  DEFAULT NULL COMMENT 'AnalysisResult 原样（ATS 7 维评分）',
    `ats_score`     INT                   DEFAULT NULL COMMENT '该版本评分 0-100',
    `change_note`   VARCHAR(256)          DEFAULT NULL,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_version_id` (`version_id`),
    UNIQUE KEY `uk_resume_version_no` (`resume_id`, `version_no`),
    KEY `idx_resume` (`resume_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '简历版本';


CREATE TABLE IF NOT EXISTS `t_resume_section` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `resume_id`     VARCHAR(32) NOT NULL,
    `version_id`    VARCHAR(32) NOT NULL,
    `section_type`  VARCHAR(32) NOT NULL COMMENT 'summary/experience/project/skill/education/other',
    `section_order` INT         NOT NULL DEFAULT 0,
    `section_title` VARCHAR(128)         DEFAULT NULL,
    `content_text`  MEDIUMTEXT          DEFAULT NULL,
    `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_resume_version` (`resume_id`, `version_id`, `section_order`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '简历段落（反范式，便于局部检索）';


-- ============================================================================
-- 3. JD
-- ============================================================================

CREATE TABLE IF NOT EXISTS `t_jd` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键，REST 路径参数用',
    `jd_id`         VARCHAR(32)  NOT NULL COMMENT '业务ID jd_xxx',
    `user_id`       VARCHAR(32)  NOT NULL,
    `source_type`   VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/URL/FILE',
    `source_url`    VARCHAR(512)          DEFAULT NULL,
    `company_name`  VARCHAR(128)          DEFAULT NULL,
    `position_name` VARCHAR(128)          DEFAULT NULL,
    `salary_range`  VARCHAR(64)           DEFAULT NULL,
    `raw_text`      MEDIUMTEXT            DEFAULT NULL COMMENT 'JD 全文',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/SUCCESS/FAILED',
    `is_deleted`    TINYINT      NOT NULL DEFAULT 0,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_jd_id` (`jd_id`),
    KEY `idx_user_status` (`user_id`, `is_deleted`),
    KEY `idx_company` (`company_name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'JD 主表';


-- JD 解析结果：raw_result_json 存 Agent 返回的 JdChunk 原样，
-- 前端直接消费，因此其余派生列仅用于检索与统计。
CREATE TABLE IF NOT EXISTS `t_jd_parse_result` (
    `id`                BIGINT      NOT NULL AUTO_INCREMENT,
    `jd_id`             VARCHAR(32) NOT NULL COMMENT '关联 t_jd.jd_id',
    `raw_result_json`   JSON                 DEFAULT NULL COMMENT 'JdChunk 原样结构',
    `hard_skills_json`  JSON                 DEFAULT NULL COMMENT '硬技能 [{name,category,importance}]',
    `soft_skills_json`  JSON                 DEFAULT NULL COMMENT '软技能',
    `biz_metrics_json`  JSON                 DEFAULT NULL COMMENT '业务指标',
    `responsibilities_json` JSON             DEFAULT NULL,
    `requirements_json` JSON                 DEFAULT NULL,
    `seniority`         VARCHAR(16)          DEFAULT NULL COMMENT 'intern/junior/mid/senior/lead',
    `industry`          VARCHAR(32)          DEFAULT NULL,
    `location`          VARCHAR(64)          DEFAULT NULL,
    `salary_min`        DECIMAL(10, 2)       DEFAULT NULL,
    `salary_max`        DECIMAL(10, 2)       DEFAULT NULL,
    `experience_min_years` DECIMAL(4, 1)     DEFAULT NULL,
    `education_required` VARCHAR(32)         DEFAULT NULL,
    `parse_score`       DECIMAL(3, 2)        DEFAULT NULL COMMENT '解析置信度 0-1',
    `parse_model`       VARCHAR(64)          DEFAULT NULL COMMENT 'parse_method 或模型名',
    `created_at`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_jd_id` (`jd_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'JD 解析结果';


CREATE TABLE IF NOT EXISTS `t_jd_skill` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `jd_id`      VARCHAR(32)  NOT NULL,
    `skill_type` VARCHAR(16)  NOT NULL COMMENT 'hard/soft/biz_metric',
    `skill_name` VARCHAR(128) NOT NULL,
    `weight`     DECIMAL(3, 2)          DEFAULT 0.50 COMMENT '重要性 0-1',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_jd_type` (`jd_id`, `skill_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'JD 技能清单（反范式，供匹配检索）';


-- ============================================================================
-- 4. 匹配
-- ============================================================================

CREATE TABLE IF NOT EXISTS `t_match_record` (
    `id`                BIGINT      NOT NULL AUTO_INCREMENT,
    `record_id`         VARCHAR(32) NOT NULL COMMENT '业务ID mch_xxx',
    `user_id`           VARCHAR(32) NOT NULL,
    `resume_id`         VARCHAR(32) NOT NULL COMMENT 't_resume.resume_id',
    `jd_id`             VARCHAR(32) NOT NULL COMMENT 't_jd.jd_id',
    `status`            VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/SUCCESS/FAILED',
    `task_id`           VARCHAR(32)          DEFAULT NULL COMMENT '关联 t_ai_task.task_id',
    `semantic_score`    INT                  DEFAULT NULL COMMENT '语义相似度 0-100',
    `keyword_score`     INT                  DEFAULT NULL COMMENT '关键词覆盖率 0-100',
    `business_score`    INT                  DEFAULT NULL COMMENT '业务指标匹配 0-100',
    `final_score`       INT                  DEFAULT NULL COMMENT '加权总分 0-100 = 0.4s+0.35k+0.25b',
    `result_json`       JSON                 DEFAULT NULL COMMENT 'MatchResult 原样',
    `error_msg`         VARCHAR(512)         DEFAULT NULL,
    `created_at`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `completed_at`      DATETIME             DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_record_id` (`record_id`),
    KEY `idx_user_created` (`user_id`, `created_at`),
    KEY `idx_pair` (`resume_id`, `jd_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '匹配记录';


CREATE TABLE IF NOT EXISTS `t_match_gap` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `record_id`    VARCHAR(32)  NOT NULL,
    `gap_type`     VARCHAR(32)  NOT NULL COMMENT 'missing_skill/weak_metric/missing_cert',
    `gap_content`  VARCHAR(256) NOT NULL,
    `severity`     TINYINT      NOT NULL DEFAULT 3 COMMENT '1-5，越大越紧急',
    `suggestion`   TEXT                  DEFAULT NULL,
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_record_severity` (`record_id`, `severity`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '匹配差距项';


-- ============================================================================
-- 5. AI 任务
--
-- status 是 VARCHAR 而不是 TINYINT：这些值会原样透出给前端和 Agent 日志，
-- 用字符串可以让 SSE、回调、前端三处读到完全一致的字面量。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `t_ai_task` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT,
    `task_id`         VARCHAR(32) NOT NULL COMMENT '业务ID task_xxx',
    `user_id`         VARCHAR(32) NOT NULL,
    `task_type`       VARCHAR(32) NOT NULL COMMENT 'jd_parse/resume_analyze/match_score/resume_optimize/interview/cover_letter/career_plan',
    `biz_id`          VARCHAR(32)          DEFAULT NULL COMMENT 'resume_id / jd_id / record_id',
    `status`          VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/CANCELLED',
    `progress`        INT         NOT NULL DEFAULT 0 COMMENT '0-100',
    `current_agent`   VARCHAR(64)          DEFAULT NULL,
    `input_payload`   JSON                 DEFAULT NULL,
    `output_payload`  JSON                 DEFAULT NULL COMMENT 'Agent 原始输出',
    `error_msg`       TEXT                 DEFAULT NULL,
    `trace_id`        VARCHAR(64)          DEFAULT NULL,
    `retry_count`     INT         NOT NULL DEFAULT 0,
    `duration_ms`     INT                  DEFAULT NULL,
    `started_at`      DATETIME             DEFAULT NULL,
    `completed_at`    DATETIME             DEFAULT NULL,
    `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_id` (`task_id`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_biz` (`biz_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 任务主表';


CREATE TABLE IF NOT EXISTS `t_agent_invocation` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `invocation_id`  VARCHAR(32)  NOT NULL COMMENT '业务ID inv_xxx',
    `task_id`        VARCHAR(32)  NOT NULL,
    `agent_name`     VARCHAR(64)  NOT NULL,
    `invocation_no`  INT          NOT NULL DEFAULT 1 COMMENT '同一任务内的调用序号',
    `model_name`     VARCHAR(64)           DEFAULT NULL,
    `status`         VARCHAR(16)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/FAILED',
    `input_payload`  JSON                  DEFAULT NULL,
    `output_payload` JSON                  DEFAULT NULL,
    `duration_ms`    INT                   DEFAULT NULL,
    `retry_attempt`  INT          NOT NULL DEFAULT 0,
    `error_msg`      TEXT                  DEFAULT NULL,
    `started_at`     DATETIME              DEFAULT NULL,
    `completed_at`   DATETIME              DEFAULT NULL,
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_invocation_id` (`invocation_id`),
    KEY `idx_task` (`task_id`, `invocation_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Agent 调用日志';


-- ============================================================================
-- 6. 模拟面试
-- ============================================================================

CREATE TABLE IF NOT EXISTS `t_interview_session` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT,
    `session_id`     VARCHAR(32) NOT NULL COMMENT '业务ID ivs_xxx',
    `user_id`        VARCHAR(32) NOT NULL,
    `resume_id`      VARCHAR(32)          DEFAULT NULL,
    `jd_id`          VARCHAR(32)          DEFAULT NULL,
    `job_title`      VARCHAR(128)         DEFAULT NULL,
    `company_name`   VARCHAR(128)         DEFAULT NULL,
    `total_rounds`   INT         NOT NULL DEFAULT 5 COMMENT '总轮数 1-8',
    `current_round`  INT         NOT NULL DEFAULT 0,
    `final_score`    INT                  DEFAULT NULL COMMENT '最终得分 0-100',
    `scorecard_json` JSON                 DEFAULT NULL COMMENT '5 维评分卡',
    `status`         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/FINISHED/ABANDONED',
    `created_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `finished_at`    DATETIME             DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_id` (`session_id`),
    KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '面试会话';


CREATE TABLE IF NOT EXISTS `t_interview_turn` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT,
    `session_id`   VARCHAR(32) NOT NULL,
    `round_no`     INT         NOT NULL,
    `question`     TEXT        NOT NULL,
    `question_type` VARCHAR(32)         DEFAULT NULL COMMENT 'behavioral/technical/followup',
    `user_answer`  MEDIUMTEXT          DEFAULT NULL,
    `score`        DECIMAL(4, 1)       DEFAULT NULL COMMENT '0-10',
    `feedback`     TEXT                 DEFAULT NULL,
    `eval_json`    JSON                 DEFAULT NULL COMMENT 'InterviewEvaluation 原样',
    `followup_of`  INT                  DEFAULT NULL COMMENT '追问自第几轮',
    `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_session_round` (`session_id`, `round_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '面试轮次';


-- ============================================================================
-- 7. 文件
-- ============================================================================

CREATE TABLE IF NOT EXISTS `t_file` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `file_id`       VARCHAR(32)  NOT NULL COMMENT '业务ID file_xxx',
    `user_id`       VARCHAR(32)  NOT NULL,
    `filename`      VARCHAR(256) NOT NULL,
    `content_type`  VARCHAR(64)           DEFAULT NULL,
    `size_bytes`    BIGINT                DEFAULT NULL,
    `storage_key`   VARCHAR(512) NOT NULL COMMENT '本地相对路径或 MinIO key',
    `upload_status` TINYINT      NOT NULL DEFAULT 1 COMMENT '0=uploading 1=completed 2=failed',
    `md5`           VARCHAR(64)           DEFAULT NULL,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_id` (`file_id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_md5` (`md5`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文件元信息';


-- ============================================================================
-- 8. 订单 / 订阅 / 通知（商业化，当前仅建表，业务由二期填充）
-- ============================================================================

CREATE TABLE IF NOT EXISTS `t_order` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `order_id`      VARCHAR(32) NOT NULL COMMENT '业务ID ord_xxx',
    `user_id`       VARCHAR(32) NOT NULL,
    `product_type`  VARCHAR(32) NOT NULL COMMENT 'membership_pro/credits',
    `amount_cents`  BIGINT      NOT NULL COMMENT '金额(分)',
    `status`        TINYINT     NOT NULL DEFAULT 0 COMMENT '0=待支付 1=已支付 2=已退款',
    `pay_channel`   VARCHAR(16)          DEFAULT NULL COMMENT 'alipay/wechat',
    `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `paid_at`       DATETIME             DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_id` (`order_id`),
    KEY `idx_user_status` (`user_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '订单';


CREATE TABLE IF NOT EXISTS `t_payment` (
    `id`               BIGINT      NOT NULL AUTO_INCREMENT,
    `payment_id`       VARCHAR(32) NOT NULL,
    `order_id`         VARCHAR(32) NOT NULL,
    `channel_trade_no` VARCHAR(64)          DEFAULT NULL,
    `paid_amount_cents` BIGINT               DEFAULT NULL,
    `paid_at`          DATETIME             DEFAULT NULL,
    `raw_response`     JSON                 DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payment_id` (`payment_id`),
    UNIQUE KEY `uk_order_id` (`order_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '支付记录';


CREATE TABLE IF NOT EXISTS `t_subscription` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `sub_id`     VARCHAR(32)  NOT NULL,
    `user_id`    VARCHAR(32)  NOT NULL,
    `order_id`   VARCHAR(32)  NOT NULL,
    `plan`       VARCHAR(32)  NOT NULL COMMENT 'pro_monthly/pro_yearly',
    `start_at`   DATETIME     NOT NULL,
    `expire_at`  DATETIME     NOT NULL,
    `auto_renew` TINYINT      NOT NULL DEFAULT 0,
    `status`     VARCHAR(16)  NOT NULL DEFAULT 'active',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sub_id` (`sub_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '订阅实例';


CREATE TABLE IF NOT EXISTS `t_notification` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT,
    `notif_id`    VARCHAR(32) NOT NULL,
    `user_id`     VARCHAR(32) NOT NULL,
    `channel`     VARCHAR(16) NOT NULL COMMENT 'email/sms/in_app',
    `template`    VARCHAR(64)          DEFAULT NULL,
    `payload`     JSON                 DEFAULT NULL,
    `status`      TINYINT     NOT NULL DEFAULT 0 COMMENT '0=pending 1=sent 2=failed',
    `sent_at`     DATETIME             DEFAULT NULL,
    `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_notif_id` (`notif_id`),
    KEY `idx_user_status` (`user_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '通知';
