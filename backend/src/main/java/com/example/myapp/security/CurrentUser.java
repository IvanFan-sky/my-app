package com.example.myapp.security;

import com.example.myapp.common.UserRole;

/**
 * 当前登录用户的最小载体。
 *
 * <p>刻意只放 id / username / role，不放整个 User 实体：实体带着 passwordHash，
 * 让它随请求到处流转，早晚会有人顺手塞进响应体里。
 *
 * <p>注入方式见 {@link LoginUser} 与 {@link CurrentUserArgumentResolver}。
 *
 * @param id       用户主键
 * @param username 用户名（登录标识）
 * @param role     角色，见 {@link UserRole}。来自缓存里的 UserVO，
 *                 因此用户被降级后会在缓存失效的下一次请求立刻生效
 */
public record CurrentUser(Long id, String username, String role) {

    /**
     * 存 HttpServletRequest attribute 里的 key。
     *
     * <p>包级可见即可：写入方是 {@link AuthInterceptor}，读取方是
     * {@link CurrentUserArgumentResolver} 与 {@link AdminOnlyInterceptor}，
     * 都在本包内，外界不该依赖这个字符串。
     */
    static final String ATTRIBUTE_NAME = "my-app.current-user";

    /** 是否管理员。null 安全的写法：role 缺失一律当作非管理员（最小权限） */
    public boolean isAdmin() {
        return UserRole.ADMIN.equals(role);
    }
}
