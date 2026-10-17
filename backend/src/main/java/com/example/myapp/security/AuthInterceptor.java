package com.example.myapp.security;

import com.example.myapp.common.ErrorCode;
import com.example.myapp.dto.UserVO;
import com.example.myapp.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 鉴权拦截器：校验 {@code Authorization: Bearer <token>}，通过则把
 * {@link CurrentUser} 写入请求属性，供 {@link CurrentUserArgumentResolver} 注入。
 *
 * <p>三个刻意为之的设计：
 * <ol>
 *   <li><b>令牌有效 ≠ 允许访问</b>。解析出 uid 后仍要确认用户还存在且未被逻辑删除。
 *       否则一个已删除用户手里的旧令牌，在有效期内可以一直用。</li>
 *   <li><b>这个确认走 Redis 缓存</b>（{@code getUserVoById}），不是每次都查库。
 *       这里是全项目最热的读路径 —— 每个受保护请求都会经过它，
 *       原先每请求一次 {@code SELECT} 是纯粹的浪费。缓存失效由 UserController 的
 *       三个写操作负责，所以「已删除用户读缓存仍存在」的漏洞已经堵上。</li>
 *   <li><b>失败时写 HTTP 状态码</b>（401），而不只是响应体里塞 code=4010。前端 axios
 *       的响应拦截器按 HTTP 状态判断是否跳登录页，HTTP 200 会让它以为请求成功。</li>
 * </ol>
 *
 * <p>另外这里**不抛 BizException**：拦截器在 DispatcherServlet 之外执行，
 * 异常不会走 {@code @RestControllerAdvice}，所以必须自己写响应。
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

        // 只接受 access token。不校验这一点的话，把有效期 7 天的 refresh token
        // 当 access 用就能绕过 15 分钟短有效期 —— 那 access 设多短都没意义。
        if (!parsed.isAccess()) {
            log.debug("用 refresh token 访问业务接口，已拒绝: userId={}", parsed.userId());
            return reject(response, "登录已失效，请重新登录");
        }

        // 走缓存：命中则完全不碰数据库
        UserVO user = userService.getUserVoById(parsed.userId());
        if (user == null) {
            // 令牌签名没问题，但用户已被删除。返回同一句话，不透露「这个 id 曾经存在」
            log.debug("令牌指向的用户不存在或已删除: id={}", parsed.userId());
            return reject(response, "登录已失效，请重新登录");
        }

        /*
         * 令牌版本号比对 —— 「改密码后旧令牌立即失效」就是靠这一句实现的。
         *
         * 令牌里带的是签发时的版本；库里是当前版本。改密码/改角色会把版本 +1，
         * 于是所有旧令牌（包括 refresh）在下一次请求就被拒。
         * 版本号从缓存里的 UserVO 取，不额外查库；改密时同步 evict，
         * 所以不存在「缓存旧版本导致撤销失效」的窗口。
         *
         * 缺省按 0 处理：V3 迁移之前创建的行 token_version 为 0，
         * 与令牌里缺省写 0 保持一致。
         */
        int currentVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        if (parsed.tokenVersion() != currentVersion) {
            log.info("令牌版本号已过期，拒绝访问（多半是改过密码或角色）: userId={} 令牌={} 当前={}",
                    parsed.userId(), parsed.tokenVersion(), currentVersion);
            return reject(response, "密码或权限已变更，请重新登录");
        }

        // role 来自缓存里的 UserVO：管理员被降级后，缓存失效的下一次请求即生效，
        // 不需要等令牌过期
        request.setAttribute(CurrentUser.ATTRIBUTE_NAME,
                new CurrentUser(user.getId(), user.getUsername(), user.getRole()));
        return true;
    }

    /**
     * 写 HTTP 401 + 统一响应体，并终止后续处理。
     *
     * <p>具体拼接交给 {@link AuthResponses}：AdminOnlyInterceptor 也要写同样的结构，
     * 抽到一处才不会出现「两个拦截器返回的 JSON 长得不一样」。
     */
    private boolean reject(HttpServletResponse response, String message) throws IOException {
        return AuthResponses.writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED, message);
    }
}
