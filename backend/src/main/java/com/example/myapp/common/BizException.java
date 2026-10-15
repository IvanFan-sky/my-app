package com.example.myapp.common;

import lombok.Getter;

/**
 * 业务异常。Service 层校验不通过时抛它，由 GlobalExceptionHandler 统一转成 ApiResponse。
 *
 * <p>不要用它替代参数校验注解（@NotNull 等）：能靠注解表达的校验，用注解。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(4000, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
