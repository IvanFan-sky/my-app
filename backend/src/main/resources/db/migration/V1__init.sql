-- ============================================================
-- V1：初始表结构（对应骨架最初的样子）
--
-- 历史的 sql/init.sql 已迁到这里，Flyway 成为**唯一**的建表来源。
-- 不要直接改已应用的迁移文件：Flyway 会校验 checksum，
-- 改动会让已有环境启动失败。要变更结构请新增 V3__xxx.sql。
-- ============================================================

CREATE TABLE `sys_user`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`      VARCHAR(64)  NOT NULL COMMENT '用户名（登录标识，唯一）',
    `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希（含盐，非明文）',
    `email`         VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `age`           INT          DEFAULT NULL COMMENT '年龄',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常 1=已删除',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_user_username` (`username`),
    KEY `idx_sys_user_created_at` (`created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='用户表';

-- 种子账号（开发期登录测试用）：
--   alice / alice123    （V2 会把她升为管理员）
--   bob   / bob123
--   carol / carol123
-- 口令只在本注释里出现，库里存的是哈希。生产环境请勿使用这些账号。
INSERT INTO `sys_user` (`username`, `password_hash`, `email`, `age`)
VALUES ('alice', '$2a$10$srpv0d6jWUG02VZgIH6GheZEgz3jJRSuMXdjhgwET46v/C.IoyNgO', 'alice@example.com', 28),
       ('bob', '$2a$10$tW7k1UvJIuyomTavZoAYx.m/Nan.ikoLECnBFiTg2M5BvIOqkf7Oq', 'bob@example.com', 31),
       ('carol', '$2a$10$l/R.affKVKR.7vYvvTywWeabDYxculqFlM7VZZ5OcOZ.V5X8J3edy', 'carol@example.com', 24);