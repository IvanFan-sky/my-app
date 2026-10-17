-- ============================================================
-- V2：用户增加 nickname / role / bio，并把 alice 设为管理员
--
-- 原来的 sql/migrate-2026-10-17-profile-role.sql 已迁到这里。
-- 注意：这一版**不是** DROP 重建，已有数据全部保留。
-- ============================================================

ALTER TABLE `sys_user`
    -- 显示名：个人中心可改，与登录用的 username 分开。
    -- 允许 NULL：老数据没有这个值，「没填显示名」是合法状态（前端回退显示 username）。
    ADD COLUMN `nickname` VARCHAR(64) NULL COMMENT '显示名（个人中心可改，不影响登录）' AFTER `username`,
    -- 角色：目前只有 ADMIN / USER。
    -- 用 VARCHAR 而不是 TINYINT：库里直接可读，将来加第三种角色不需要位运算。
    -- 默认 USER：新增用户默认没有管理权限，符合最小权限原则。
    ADD COLUMN `role` VARCHAR(16) NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN=管理员，USER=普通用户' AFTER `password_hash`,
    ADD COLUMN `bio` VARCHAR(255) NULL COMMENT '个人简介' AFTER `email`;

-- 把种子管理员设为 alice。
-- 全新安装时这一步很关键：没有管理员，所有 /api/users 接口都会 403，
-- 装完就没人能进用户管理（只能改库）。
UPDATE `sys_user` SET `role` = 'ADMIN' WHERE `username` = 'alice';

-- 顺手给管理员补一个显示名与简介，个人中心打开就有内容（纯展示，可改）
UPDATE `sys_user` SET `nickname` = '爱丽丝', `bio` = '管理员账号' WHERE `username` = 'alice';