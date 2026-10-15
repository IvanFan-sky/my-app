package com.example.myapp.security;

import com.example.myapp.common.ErrorCode;
import com.example.myapp.entity.User;
import com.example.myapp.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 鉴权拦截器：校验 {@code Authorization: Bearer <token>}，通过则把
 * {@link CurrentUser} 写入请求属性，供 {@link CurrentUserArgumentResolver} 注入。
 *
 * <p>三个刻意为之的设计：
 * <ol>
 *   <li><b>令牌有效 ≠ 允许访问</b>。解析出 uid 后仍会查一次库，确认用户还存在且未被
 *       逻辑删除。否则一个已删除用户手里的旧令牌，在有效期内可以一直用。</li>
 *   <li><b>失败时写 HTTP 状态码</b>（401），而不只是响应体里塞 code=4010。前端 axios
 *       的响应拦截器按 HTTP 状态判断是否跳登录页，HTTP 200 会让它以为请求成功。</li>
 *   <li><b>这里不抛 BizException</b>。拦截器在 DispatcherServlet 之外执行，
 *       异常不会走 {@code @RestControllerAdvice}，所以必须自己写响应。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // 浏览器跨域预检不带 Authorization，放行，否则所有跨域请求都会失败
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return reject(response, "请先登录");
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return reject(response, "请先登录");
        }

        JwtTokenProvider.ParsedToken parsed;
        try {
            parsed = tokenProvider.parse(token);
        } catch (TokenInvalidException e) {
            log.debug("令牌校验失败: {}", e.getMessage());
            return reject(response, "登录已失效，请重新登录");
        }

        User user = userService.getById(parsed.userId());
        if (user == null) {
            // 令牌签名没问题，但用户已被删除。返回同一句话，不透露「这个 id 曾经存在」
            log.debug("令牌指向的用户不存在或已删除: id={}", parsed.userId());
            return reject(response, "登录已失效，请重新登录");
        }

        request.setAttribute(CurrentUser.ATTRIBUTE_NAME, new CurrentUser(user.getId(), user.getUsername()));
        return true;
    }

    /** 写 HTTP 401 + 统一响应体，并终止后续处理 */
    private boolean reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // 手工拼 JSON：这里不能用 ObjectMapper（拦截器不是 Spring MVC 的返回值处理流程），
        // message 是本类内写死的常量，不含用户输入，因此没有注入风险
        response.getWriter().write("{\"code\":" + ErrorCode.UNAUTHORIZED
                + ",\"message\":\"" + message + "\",\"data\":null}");
        return false;
    }
}
