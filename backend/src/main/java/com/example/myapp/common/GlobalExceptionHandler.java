package com.example.myapp.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理：把异常统一翻译成 ApiResponse，避免每个 Controller 写 try/catch。
 *
 * <p>注意：这里始终返回 HTTP 200 + 业务 code。若希望失败也走真实 HTTP 状态码，
 * 在 @ExceptionHandler 上补 @ResponseStatus，或返回 ResponseEntity。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** @Valid 校验失败：把字段错误拼成一条可读提示 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + defaultMessage(fe))
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", detail);
        return ApiResponse.fail(4000, detail);
    }

    /** 业务异常：保留自定义 code */
    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBiz(BizException e) {
        log.warn("业务异常 code={} msg={}", e.getCode(), e.getMessage());
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    /** 兜底：不要把这行删掉，否则异常会以 HTML 错误页返回，前端拿到的是栈信息 */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleOther(Exception e) {
        log.error("未预期异常", e);
        return ApiResponse.fail(5000, "服务器内部错误");
    }

    private static String defaultMessage(FieldError fe) {
        return fe.getDefaultMessage() == null ? "参数不合法" : fe.getDefaultMessage();
    }
}
