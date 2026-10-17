package com.example.myapp.dto;

import lombok.Data;

/**
 * 刷新令牌请求体。
 *
 * <p>为什么不从 Authorization 头里取 refresh token：那里放的是 access，
 * 两者的用途和有效期完全不同，混用同一个传输位置会让「拦截器该验证哪种令牌」
 * 变得含糊 —— 而这类含糊正是越权漏洞的温床。放在请求体里，语义一目了然。
 *
 * <p>刻意不加 {@code @NotBlank}：如果传入空值，{@code AuthService.refresh}
 * 会返回统一的 4010「登录已失效」。用校验注解的话会变成 4000（参数不合法），
 * 前端就要为两种情况写两套处理，而本质上是同一件事。
 */
@Data
public class RefreshForm {

    private String refreshToken;
}
