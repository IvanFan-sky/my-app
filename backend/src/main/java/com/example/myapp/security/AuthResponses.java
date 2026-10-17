package com.example.myapp.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 拦截器里写错误响应的公共工具。
 *
 * <p>为什么需要它：拦截器运行在 DispatcherServlet 之外，**异常不会走
 * {@code @RestControllerAdvice}**，所以没法抛 BizException 让全局处理器翻译。
 * 于是每个拦截器都得自己拼 JSON —— 抽出来就是为了不把同一段拼接代码复制两遍
 * （复制出去的第二份，早晚会在改文案时被漏掉）。
 *
 * <p>安全性说明：message 由调用方传入，**必须是不含用户输入的常量**。
 * 这里直接字符串拼接而非走 Jackson，因此没有做转义 —— 若将来要传动态内容，
 * 必须先换成用 ObjectMapper 序列化，否则会有 JSON 注入。
 */
final class AuthResponses {

    private AuthResponses() {
    }

    /** 写一个 {@code {code, message, data:null}} 响应并返回 false，供拦截器直接 return */
    static boolean writeError(HttpServletResponse response, int httpStatus, int code, String message)
            throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":" + code
                + ",\"message\":\"" + message + "\",\"data\":null}");
        return false;
    }
}
