package com.example.myapp.controller;

import com.example.myapp.common.ApiResponse;
import com.example.myapp.common.BizException;
import com.example.myapp.common.ErrorCode;
import com.example.myapp.dto.LoginForm;
import com.example.myapp.dto.LoginResponse;
import com.example.myapp.dto.UserVO;
import com.example.myapp.security.CurrentUser;
import com.example.myapp.security.LoginUser;
import com.example.myapp.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口，路径前缀 /api/auth。
 *
 * <p>登录接口在拦截器白名单里（见 WebMvcConfig）；/me 需要携带令牌。
 * 口令校验、令牌签发都在 AuthService，这里只做参数接收与结果包装。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * POST /api/auth/login
     *
     * <p>成功返回 {@code {token, tokenType, expiresIn, user}}。
     * 失败一律返回 code 4010 且文案相同（不区分用户不存在 / 密码错误）。
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginForm form) {
        return ApiResponse.ok(authService.login(form));
    }

    /**
     * GET /api/auth/me —— 返回当前令牌对应的用户公开信息。
     *
     * <p>前端刷新页面后靠它确认登录态是否还有效。参数由
     * {@link com.example.myapp.security.CurrentUserArgumentResolver} 注入，
     * 能进到这里说明拦截器已经校验过令牌并查过库了。
     */
    @GetMapping("/me")
    public ApiResponse<UserVO> me(@LoginUser CurrentUser currentUser) {
        // 理论上拦截器保证它非空；真为 null 说明拦截器被摘掉了，必须显式失败而不是放行
        if (currentUser == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return ApiResponse.ok(authService.getCurrentUser(currentUser.id(), currentUser.username()));
    }
}
