package com.example.myapp.security;

import com.example.myapp.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 管理员校验：挂在 `/api/users/**` 上，要求当前用户 role = ADMIN。
 *
 * <p><b>为什么单独一个拦截器，而不是塞进 AuthInterceptor</b>：
 * 鉴权（你是谁）与授权（你能不能做）是两件事。分开后 <li>AuthInterceptor 只负责解析令牌，
 * 不关心业务路径；</li><li>这里只负责角色判断，不关心令牌怎么来的。</li>
 * 混在一起，将来加「某些接口只要登录、某些要管理员」时，条件会越叠越乱。
 *
 * <p><b>为什么必须注册在 AuthInterceptor 之后</b>：这里依赖前者写入的
 * {@link CurrentUser} 属性。注册顺序反了会读不到用户、把管理员也拦掉。
 *
 * <p><b>403 而不是 401</b>：401 的语义是「没有有效身份」，会让前端跳登录页；
 * 而这里的用户身份完全有效，只是权限不够，跳登录页是错误引导。
 */
@Slf4j
@Component
public class AdminOnlyInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // 预检请求不带 Authorization，也不是真实业务调用，直接放行让 CORS 逻辑处理
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        CurrentUser currentUser = (CurrentUser) request.getAttribute(CurrentUser.ATTRIBUTE_NAME);
        if (currentUser == null) {
            // 理论上不会发生：AuthInterceptor 已经从令牌里放进来过。
            // 真发生了说明拦截器注册顺序被改动过，显式报 401 而不是静默放行。
            log.warn("AdminOnlyInterceptor 读不到 CurrentUser，检查拦截器注册顺序 path={}", request.getRequestURI());
            return AuthResponses.writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    ErrorCode.UNAUTHORIZED, "请先登录");
        }

        if (!currentUser.isAdmin()) {
            log.info("拒绝非管理员访问管理接口 userId={} username={} path={}",
                    currentUser.id(), currentUser.username(), request.getRequestURI());
            return AuthResponses.writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    ErrorCode.FORBIDDEN, "需要管理员权限");
        }
        return true;
    }
}
