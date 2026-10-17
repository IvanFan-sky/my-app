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
    `username`      VARCHAR(64)  NOT NULL COMMENT '用户名（登录标识，唯一）',
    `nickname`      VARCHAR(64)  DEFAULT NULL COMMENT '显示名（个人中心可改，不影响登录）',
    `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希（含盐，非明文）',
    `role`          VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN=管理员，USER=普通用户',
    `email`         VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `bio`           VARCHAR(255) DEFAULT NULL COMMENT '个人简介',
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
-- alice 是唯一的管理员：role 必须显式给 ADMIN，否则管理接口全部 403，
-- 全新安装后就没有任何账号能进「用户管理」。
INSERT INTO `sys_user` (`username`, `nickname`, `password_hash`, `role`, `email`, `bio`, `age`)
VALUES ('alice', '爱丽丝', '$2a$10$srpv0d6jWUG02VZgIH6GheZEgz3jJRSuMXdjhgwET46v/C.IoyNgO', 'ADMIN',
        'alice@example.com', '管理员账号', 28),
       ('bob', NULL, '$2a$10$tW7k1UvJIuyomTavZoAYx.m/Nan.ikoLECnBFiTg2M5BvIOqkf7Oq', 'USER',
        'bob@example.com', NULL, 31),
       ('carol', NULL, '$2a$10$l/R.affKVKR.7vYvvTywWeabDYxculqFlM7VZZ5OcOZ.V5X8J3edy', 'USER',
        'carol@example.com', NULL, 24);

-- 刻意不在这里 SELECT 全表：该语句只方便人肉看一眼，却会把结果混进构建/部署日志。
-- 要确认结果，进客户端执行：SELECT id, username, email, age FROM sys_user ORDER BY id;
