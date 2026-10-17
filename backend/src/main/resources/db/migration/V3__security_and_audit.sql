-- ============================================================
-- V3：安全加固与审计
--
-- 三件事：
--   1. email 加索引，并把搜索改成前缀匹配（让索引真正生效）
--   2. token_version：令牌版本号，改密/改角色后旧令牌立即失效
--   3. sys_audit_log：审计日志（写操作 + 登录）
--
-- 为什么要一起做：这三项共同支撑「令牌失效 + 可追溯」这一组语义，
-- 分成三个迁移会让中间态说不清（比如有版本号但没审计，改密后查不到是谁改的）。
-- ============================================================

-- ------------------------------------------------------------
-- 1. email 索引 + 搜索策略
-- ------------------------------------------------------------
-- 原来的搜索是 like '%kw%'（两侧通配），这种写法**任何 B-Tree 索引都用不上**，
-- 加索引也不会变快。所以这里同时做两件事：加索引 + 把搜索改成 like 'kw%'（前缀）。
--
-- 代价（已与产品确认）：不能再用「中间的片段」搜索。比如搜 example 找不到
-- alice@example.com，必须搜 alice 或 alice@。这是用「可预测的性能」换
-- 「更强的模糊匹配」，用户量上千后这个取舍是值得的。
CREATE INDEX `idx_sys_user_email` ON `sys_user` (`email`);

-- ------------------------------------------------------------
-- 2. 令牌版本号
-- ------------------------------------------------------------
-- 语义：JWT 里带上签发时的 token_version；拦截器比对「令牌里的版本」与
-- 「库里当前版本」，不一致就拒绝。改密码 / 改角色 / 删除账号时把版本 +1，
-- 于是**所有已签发的旧令牌立即失效**（refresh token 也一起失效）。
--
-- 为什么用「版本号」而不是「Redis 黑名单」：
--   - 黑名单要为每个令牌存一条记录，还要处理过期清理；版本号是每用户一个整数。
--   - 黑名单丢失（Redis 清空/重启）会让已撤销的令牌复活；版本号在数据库里，持久。
-- 代价：撤销是「全设备」级别的 —— 用户改密码后，其他设备也得重新登录。
-- 这正是改密码时通常想要的语义；如果需要「只踢掉某一台设备」，那要按会话 id 记录，
-- 属独立需求。
ALTER TABLE `sys_user`
    ADD COLUMN `token_version` INT NOT NULL DEFAULT 0
        COMMENT '令牌版本号：改密/改角色时 +1，使已签发的令牌全部失效';

-- ------------------------------------------------------------
-- 3. 审计日志
-- ------------------------------------------------------------
-- 只记「写操作 + 登录」，不记查询：查询量级完全不同（每次翻页一条），
-- 而「谁改了谁」才是审计真正要回答的问题。
--
-- 设计取舍：
--   - actor_username / target_username 冗余存一份字符串，而不是只存 id。
--     用户被改名或删除后，日志仍然能读懂「当时是谁对谁做了什么」——
--     审计日志如果依赖 join 现有用户表，一旦对方被删，历史就变成一堆孤儿 id。
--   - detail 存 JSON 字符串而不是拆成多列：不同动作要记的字段不一样
--     （改密码不该记密码，改资料要记改了哪些字段），拆列会变成一堆稀疏字段。
--   - 不做逻辑删除：审计日志只增不改，这是它可信的前提。
--   - created_at 只留创建时间，没有 updated_at：日志不该被更新。
CREATE TABLE `sys_audit_log`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `actor_id`        BIGINT       DEFAULT NULL COMMENT '操作人 id；登录失败时可能为空',
    `actor_username`  VARCHAR(64)  DEFAULT NULL COMMENT '操作人用户名（冗余，防用户被删后读不懂）',
    `action`          VARCHAR(32)  NOT NULL COMMENT '动作：LOGIN/LOGIN_FAIL/REGISTER/CREATE_USER/UPDATE_USER/DELETE_USER/UPDATE_PROFILE/CHANGE_PASSWORD/BATCH_DELETE',
    `target_type`     VARCHAR(32)  DEFAULT NULL COMMENT '对象类型：USER 等',
    `target_id`       BIGINT       DEFAULT NULL COMMENT '对象 id',
    `target_username` VARCHAR(64)  DEFAULT NULL COMMENT '对象用户名（冗余）',
    `detail`          VARCHAR(1000) DEFAULT NULL COMMENT '补充信息（JSON 字符串），不要放密码等敏感值',
    `ip`              VARCHAR(64)  DEFAULT NULL COMMENT '来源 IP',
    `success`         TINYINT      NOT NULL DEFAULT 1 COMMENT '是否成功：0=失败 1=成功',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (`id`),
    KEY `idx_audit_created_at` (`created_at`),
    KEY `idx_audit_actor` (`actor_id`, `created_at`),
    KEY `idx_audit_action` (`action`, `created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='审计日志（写操作与登录，只增不改）';
