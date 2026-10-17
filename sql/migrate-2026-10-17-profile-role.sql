-- ============================================================
-- 迁移：sys_user 增加 nickname / role / bio
--
-- 与 init.sql 的区别：本脚本**不 DROP 表**，已有数据全部保留。
-- init.sql 是「全新安装」用的（会先 DROP），日常演进请用本类迁移脚本。
--
-- 用法：
--   & 'F:\JavaTool\MySQL84\bin\mysql.exe' -u root -p < sql\migrate-2026-10-17-profile-role.sql
--
-- ⚠️ ALTER TABLE ... ADD COLUMN **不可重入**：重复执行会报
--    ERROR 1060 (42S21): Duplicate column name 'nickname'
--    这是预期行为。要回滚请执行文件末尾注释里的反向语句。
-- ============================================================

USE `my_app`;

ALTER TABLE `sys_user`
    -- 显示名：个人中心里可改，与登录用的 username 分开。
    -- 允许 NULL：老数据没有这个值，且「没填显示名」是合法状态（前端回退显示 username）。
    ADD COLUMN `nickname` VARCHAR(64) NULL COMMENT '显示名（个人中心可改，不影响登录）' AFTER `username`,
    -- 角色：目前只有 ADMIN / USER 两种。
    -- 用 VARCHAR 而不是 TINYINT：库里直接可读，将来加第三种角色不需要做位运算。
    -- 默认 USER：新增用户默认没有管理权限，符合最小权限原则。
    ADD COLUMN `role` VARCHAR(16) NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN=管理员，USER=普通用户' AFTER `password_hash`,
    ADD COLUMN `bio` VARCHAR(255) NULL COMMENT '个人简介' AFTER `email`;

-- 把种子管理员设为 alice。
-- 注意：这只在「alice 存在」时才生效；你库里若已删掉 alice，请自行指定一个管理员，
-- 否则所有管理接口都会 403（届时任何人都进不了用户管理）。
UPDATE `sys_user` SET `role` = 'ADMIN' WHERE `username` = 'alice';

-- 迁移结果自查
SELECT `id`, `username`, `nickname`, `role`,
       (LEFT(`bio`, 20)) AS bio_prefix, `deleted`
FROM `sys_user`
ORDER BY `id`;

-- ------------------------------------------------------------
-- 回滚（需要时手动执行）：
--   ALTER TABLE `sys_user`
--       DROP COLUMN `nickname`,
--       DROP COLUMN `role`,
--       DROP COLUMN `bio`;
-- ------------------------------------------------------------
