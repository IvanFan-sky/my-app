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
 *   <li>4009 —— 资源冲突（用户名已被占用等）</li>
 *   <li>4010 —— 认证相关（未登录、登录失败、令牌无效）</li>
 *   <li>4030 —— 授权相关（已登录但无权限）</li>
 *   <li>4290 —— 请求过于频繁（登录失败次数过多触发临时锁定）</li>
 *   <li>5000 —— 服务端自身异常</li>
 * </ul>
 */
public final class ErrorCode {

    /** 参数校验失败（@Valid 失败、表单字段不合法） */
    public static final int PARAM_INVALID = 4000;

    /**
     * 资源冲突，例如注册时用户名已被占用。
     *
     * <p>HTTP 语义上对应 409，但本项目约定「HTTP 200 + 业务 code」
     * （见 GlobalExceptionHandler 的说明），因此这里仍是业务码。
     */
    public static final int CONFLICT = 4009;

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

    /**
     * 请求过于频繁：登录连续失败次数超限，账号被临时锁定。
     *
     * <p>为什么单列一个码而不是复用 4010：前端对 4010 的处理是「清登录态并跳登录页」，
     * 而这里的用户并没有登录态问题，只是被限流了。混用会让「被锁几分钟」
     * 表现成「突然被登出」，用户完全无法理解发生了什么。
     *
     * <p>按项目约定仍是 HTTP 200 + 业务码（见 GlobalExceptionHandler）。
     */
    public static final int TOO_MANY_ATTEMPTS = 4290;

    /** 未预期异常 */
    public static final int SERVER_ERROR = 5000;

    private ErrorCode() {
        // 常量类，不允许实例化
    }
}
