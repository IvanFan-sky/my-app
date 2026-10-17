package com.example.myapp.security;

import com.example.myapp.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;

/**
 * 角色校验：读取端点上的 {@link RequireRole} 注解，判断当前用户角色是否满足。
 *
 * <p><b>注册在 {@code /api/**} 上，但只对带注解的端点生效</b>。这样新增接口默认
 * 只需登录；要收紧权限时在方法或类上标一个注解即可，不用回来改配置。
 *
 * <p><b>必须注册在 {@link AuthInterceptor} 之后</b>：这里依赖前者写入的
 * {@link CurrentUser}。顺序反了会读不到用户，把管理员也拦掉。
 *
 * <p><b>403 而不是 401</b>：401 的语义是「没有有效身份」，会让前端跳登录页；
 * 而这里的用户身份完全有效，只是权限不够，跳登录页是错误引导。
 */
@Slf4j
@Component
public class RoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // 预检请求不带 Authorization，也不是真实业务调用，直接放行让 CORS 逻辑处理
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        // 静态资源、错误页等不是 HandlerMethod，没有注解可读，交给后续链路
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireRole requireRole = resolveAnnotation(handlerMethod);
        if (requireRole == null) {
            // 没标注解 = 只要登录即可。登录态已由 AuthInterceptor 保证，这里直接放行。
            return true;
        }

        CurrentUser currentUser = (CurrentUser) request.getAttribute(CurrentUser.ATTRIBUTE_NAME);
        if (currentUser == null) {
            // 理论上不会发生：AuthInterceptor 已经放进来过。
            // 真发生了说明拦截器顺序被改动，显式报 401 而不是静默放行。
            log.warn("RoleInterceptor 读不到 CurrentUser，检查拦截器注册顺序 path={}", request.getRequestURI());
            return AuthResponses.writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    ErrorCode.UNAUTHORIZED, "请先登录");
        }

        String[] allowed = requireRole.value();
        // 空数组 = 任意已登录用户。写出来只是表达「这里确实考虑过权限」。
        if (allowed.length == 0 || Arrays.asList(allowed).contains(currentUser.role())) {
            return true;
        }

        log.info("拒绝访问：角色不满足 userId={} username={} role={} 需要={} path={}",
                currentUser.id(), currentUser.username(), currentUser.role(),
                Arrays.toString(allowed), request.getRequestURI());
        return AuthResponses.writeError(response, HttpServletResponse.SC_FORBIDDEN,
                ErrorCode.FORBIDDEN, "需要管理员权限");
    }

    /**
     * 取生效的注解：**方法上的优先于类上的**。
     *
     * <p>这样可以在「整组要求管理员」的 Controller 里，给某一个方法放宽到更多角色，
     * 而不用把类上的注解删掉、再逐个方法补。
     */
    private static RequireRole resolveAnnotation(HandlerMethod handlerMethod) {
        RequireRole onMethod = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (onMethod != null) {
            return onMethod;
        }
        return handlerMethod.getBeanType().getAnnotation(RequireRole.class);
    }
}
