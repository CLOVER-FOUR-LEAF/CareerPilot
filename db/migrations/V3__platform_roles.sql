-- ============================================================================
-- CareerPilot · 平台化拆分（角色 / 管理端配置）
--
-- 对应「三端」拆分：
--   USER      求职者（C 端）
--   RECRUITER 招聘方（HR 端，与 C 端共用 web/ 前端，按路由隔离）
--   ADMIN     平台运营（admin-web/ 独立前端）
--
-- 原则：
--   * 只做增量，不改既有列语义（V1/V2 保持原样）
--   * 角色是「授权维度」，不是「用户类型」—— 同一个账号可以被提升为
--     RECRUITER，但不应因此读不到自己的求职数据
--   * 企业 / 租户（org_id）本期不建，留给 B 端协作需求明确后再加
-- ============================================================================

ALTER TABLE `t_user`
    ADD COLUMN `role` VARCHAR(16) NOT NULL DEFAULT 'USER'
        COMMENT 'USER/RECRUITER/ADMIN' AFTER `status`;

CREATE INDEX `idx_user_role` ON `t_user` (`role`);


-- ============================================================================
-- 平台配置（管理端维护，Agent / 业务服务读取）
--
-- 用 key -> JSON 的形态，避免每加一个开关就改一次表结构：
--   llm.provider        模型接入配置（baseUrl / model / temperature / enabled）
--   feature.flags       功能开关
--   platform.branding   站点名称、客服信息等
--   screening.policy    HR 筛选的合规策略（保留期、是否允许自动排序等）
--
-- 注意：**不要在这里存明文 API Key**。密钥走部署环境的 secret，
-- 这里只存「用哪个模型、开不开」这类可公开的配置。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `t_platform_config` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `config_key`    VARCHAR(64)  NOT NULL COMMENT '配置键，如 llm.provider',
    `config_value`  JSON         DEFAULT NULL COMMENT '配置值（JSON）',
    `description`   VARCHAR(255) DEFAULT NULL COMMENT '给运营看的说明',
    `updated_by`    VARCHAR(32)  DEFAULT NULL COMMENT '最后修改人 user_id',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '平台配置';


-- 初始化默认配置（幂等：已存在则跳过）
INSERT INTO `t_platform_config` (`config_key`, `config_value`, `description`)
VALUES
    ('llm.provider',
     JSON_OBJECT(
        'enabled', FALSE,
        'baseUrl', 'https://api.openai.com/v1',
        'model', 'gpt-4o',
        'temperature', 0.3
     ),
     '大模型接入配置；enabled=false 时全部走规则引擎'),
    ('feature.flags',
     JSON_OBJECT(
        'hrScreening', TRUE,
        'autoReject', FALSE,
        'androidClient', TRUE
     ),
     '功能开关；autoReject 默认关闭（合规边界）'),
    ('platform.branding',
     JSON_OBJECT('name', 'CareerPilot', 'supportEmail', ''),
     '站点品牌与客服信息')
ON DUPLICATE KEY UPDATE `config_key` = VALUES(`config_key`);


-- ============================================================================
-- 管理审计（管理端敏感操作留痕）
-- ============================================================================

CREATE TABLE IF NOT EXISTS `t_admin_audit_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `audit_id`      VARCHAR(32)  NOT NULL COMMENT '业务ID aud_xxx',
    `actor_id`      VARCHAR(32)  NOT NULL COMMENT '操作人 user_id',
    `action`        VARCHAR(64)  NOT NULL COMMENT '如 user.role.update',
    `target_type`   VARCHAR(32)  DEFAULT NULL,
    `target_id`     VARCHAR(64)  DEFAULT NULL,
    `detail_json`   JSON         DEFAULT NULL,
    `ip`            VARCHAR(64)  DEFAULT NULL,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_audit_id` (`audit_id`),
    KEY `idx_actor_time` (`actor_id`, `created_at`),
    KEY `idx_action_time` (`action`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '管理端审计日志';
