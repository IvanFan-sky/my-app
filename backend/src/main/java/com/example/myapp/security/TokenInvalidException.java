package com.example.myapp.security;

/**
 * 令牌不合法（签名错误 / 已过期 / 格式不对 / 缺 claim）。
 *
 * <p>刻意不继承 BizException：这一层是纯技术组件，不该依赖业务异常体系。
 * 由调用方（AuthService）决定翻译成哪个业务错误码，HTTP 语义留在 Web 层。
 */
public class TokenInvalidException extends RuntimeException {

    public TokenInvalidException(String message) {
        super(message);
    }

    public TokenInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
