package com.example.myapp.common;

/**
 * 业务错误码登记处。
 *
 * <p>AGENTS.md 第 7 节的约定：新增业务码必须在这里登记，不要在各处随手写数字。
 * 集中在这里的好处是「有没有撞码」一眼可查 —— 4004 和 4040 这类看起来像的码，
 * 分散在几个文件里时很容易重复。
 *
 * <p>分段规则（沿用 4xxx 业务失败 / 5xxx 系统异常）：
 * <ul>
 *   <li>0 —— 成功</li>
 *   <li>4000 —— 参数校验失败</li>
 *   <li>4004 —— 资源不存在（沿用骨架里已有的码，不要改成 4040）</li>
 *   <li>4010 —— 认证相关（未登录、登录失败、令牌无效）</li>
 *   <li>4030 —— 授权相关（已登录但无权限，本版本还没用到，先登记占位）</li>
 *   <li>5000 —— 服务端自身异常</li>
 * </ul>
 */
public final class ErrorCode {

    /** 参数校验失败（@Valid 失败、表单字段不合法） */
    public static final int PARAM_INVALID = 4000;

    /**
     * 未认证。用户名或密码错误、缺少令牌、令牌无效或过期，都用这个码。
     *
     * <p>「用户不存在」和「密码错误」刻意共用一个码与一句文案：
     * 区分开等于给攻击者一个免费的用户名枚举接口。
     */
    public static final int UNAUTHORIZED = 4010;

    /** 已认证但无权限执行该操作（预留给角色 / 权限功能） */
    public static final int FORBIDDEN = 4030;

    /** 资源不存在 */
    public static final int NOT_FOUND = 4004;

    /** 未预期异常 */
    public static final int SERVER_ERROR = 5000;

    private ErrorCode() {
        // 常量类，不允许实例化
    }
}
