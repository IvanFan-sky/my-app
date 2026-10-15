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

CREATE TABLE `sys_user`
(
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`   VARCHAR(64)  NOT NULL COMMENT '用户名',
    `email`      VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `age`        INT          DEFAULT NULL COMMENT '年龄',
    `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常 1=已删除',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_user_username` (`username`),
    KEY `idx_sys_user_created_at` (`created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='用户表';

INSERT INTO `sys_user` (`username`, `email`, `age`)
VALUES ('alice', 'alice@example.com', 28),
       ('bob', 'bob@example.com', 31),
       ('carol', 'carol@example.com', 24);

SELECT `id`, `username`, `email`, `age`, `created_at` FROM `sys_user` ORDER BY `id`;
