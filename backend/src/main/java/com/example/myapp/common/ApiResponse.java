package com.example.myapp.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应体。所有 Controller 都返回它，前端只需判断 code。
 *
 * <p>约定：code = 0 表示成功，非 0 表示业务失败（HTTP 状态码仍可能是 200）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    /** 0 = 成功；非 0 = 业务错误码 */
    private int code;

    /** 给前端/用户看的提示文案 */
    private String message;

    /** 业务数据，失败时为 null */
    private T data;

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
