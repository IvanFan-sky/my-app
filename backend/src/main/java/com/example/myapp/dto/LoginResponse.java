package com.example.myapp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应体。
 *
 * <p>tokenType 固定为 Bearer，前端据此拼 {@code Authorization: Bearer <token>}。
 * 把它放进响应而不是让前端写死，是为了将来换认证方案（比如 DPoP）时前端不用改。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /** 访问令牌（access）：有效期短，随每个业务请求发送 */
    private String token;

    /**
     * 刷新令牌（refresh）：有效期长，**只在换 access 时提交**，不随业务请求发送。
     *
     * <p>它同样受 token_version 约束：改密码会让所有已签发的 refresh 一起失效。
     */
    private String refreshToken;

    /** 令牌类型，固定 Bearer */
    private String tokenType;

    /** 有效期（秒），前端可据此做提前刷新或到期提示 */
    private long expiresIn;

    /** 登录用户的公开信息（不含任何密码字段） */
    private UserVO user;
}
