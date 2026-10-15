package com.example.myapp.security;

/**
 * 当前登录用户的最小载体。
 *
 * <p>刻意只放 id 与 username，不放整个 User 实体：实体带着 passwordHash，
 * 让它随请求到处流转，早晚会有人顺手塞进响应体里。
 *
 * <p>注入方式见 {@link LoginUser} 与 {@link CurrentUserArgumentResolver}。
 *
 * @param id       用户主键
 * @param username 用户名
 */
public record CurrentUser(Long id, String username) {

    /**
     * 存在 HttpServletRequest attribute 里的 key。
     *
     * <p>包级可见即可：写入方是 {@link AuthInterceptor}，读取方是
     * {@link CurrentUserArgumentResolver}，都在本包内，外界不该依赖这个字符串。
     */
    static final String ATTRIBUTE_NAME = "my-app.current-user";
}
