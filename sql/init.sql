-- ============================================================
-- my-app 数据库初始化脚本（MySQL 8.4）
--
-- 用法二选一：
--   1) 命令行（推荐，密码会提示输入）：
--        & 'F:\JavaTool\MySQL84\bin\mysql.exe' -u root -p < sql\init.sql
--   2) 已登录 mysql 客户端里：
--        source J:/dsh-NewWork/my-app/sql/init.sql
--
-- ⚠️ 警告：本脚本会 DROP 掉 sys_user 表，重新执行会丢失该表全部数据。
--          正式环境请改用 Flyway / Liquibase 之类带版本号的迁移工具。
-- ============================================================

CREATE DATABASE IF NOT EXISTS `my_app`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE `my_app`;

DROP TABLE IF EXISTS `sys_user`;

-- password_hash 是 BCrypt 哈希（本身含盐、定长 60 字符），**不是明文，也不是可解密的密文**。
-- 校验只能在服务端用 BCryptPasswordEncoder.matches(明文, 哈希) 做。
-- 给 VARCHAR(100) 留余量：将来把强度从 10 提到更高，或换成 $2b$ 前缀，长度仍够用。
CREATE TABLE `sys_user`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`      VARCHAR(64)  NOT NULL COMMENT '用户名',
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
--   alice / alice123
--   bob   / bob123
--   carol / carol123
-- 口令只在本注释里出现，库里存的是哈希。生产环境请勿使用这些账号。
INSERT INTO `sys_user` (`username`, `password_hash`, `email`, `age`)
VALUES ('alice', '$2a$10$srpv0d6jWUG02VZgIH6GheZEgz3jJRSuMXdjhgwET46v/C.IoyNgO', 'alice@example.com', 28),
       ('bob', '$2a$10$tW7k1UvJIuyomTavZoAYx.m/Nan.ikoLECnBFiTg2M5BvIOqkf7Oq', 'bob@example.com', 31),
       ('carol', '$2a$10$l/R.affKVKR.7vYvvTywWeabDYxculqFlM7VZZ5OcOZ.V5X8J3edy', 'carol@example.com', 24);

-- 刻意不在这里 SELECT 全表：该语句只方便人肉看一眼，却会把结果混进构建/部署日志。
-- 要确认结果，进客户端执行：SELECT id, username, email, age FROM sys_user ORDER BY id;
