package com.example.myapp.controller;

import com.example.myapp.common.ApiResponse;
import com.example.myapp.common.BizException;
import com.example.myapp.common.ErrorCode;
import com.example.myapp.dto.LoginForm;
import com.example.myapp.dto.LoginResponse;
import com.example.myapp.dto.PasswordForm;
import com.example.myapp.dto.ProfileForm;
import com.example.myapp.dto.RefreshForm;
import com.example.myapp.dto.RegisterForm;
import com.example.myapp.dto.UserVO;
import com.example.myapp.security.CurrentUser;
import com.example.myapp.security.LoginUser;
import com.example.myapp.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
     * POST /api/auth/register
     *
     * <p>注册成功直接返回令牌（注册即登录），响应结构与登录一致。
     * 用户名被占用返回 code 4009。
     */
    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterForm form) {
        return ApiResponse.ok(authService.register(form));
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

    /**
     * PUT /api/auth/profile —— 修改**自己**的资料。
     *
     * <p>注意路径里**没有 id**，用户身份只从令牌取。
     * 这样即使有人想改别人的资料，也没有可以篡改的参数 —— 越权在结构上不成立，
     * 而不是靠一句「记得校验一下 id 是否等于自己」。
     */
    @PutMapping("/profile")
    public ApiResponse<UserVO> updateProfile(@LoginUser CurrentUser currentUser,
                                             @Valid @RequestBody ProfileForm form) {
        if (currentUser == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return ApiResponse.ok(authService.updateProfile(currentUser.id(), form));
    }

    /**
     * PUT /api/auth/password —— 修改**自己**的密码，需要提供原密码。
     *
     * <p>原密码不正确返回 4000（不是 4010），避免前端把它当成登录失效而强制登出。
     */
    @PutMapping("/password")
    public ApiResponse<LoginResponse> changePassword(@LoginUser CurrentUser currentUser,
                                                     @Valid @RequestBody PasswordForm form) {
        if (currentUser == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        // 返回新的令牌对：改密会作废该用户全部旧令牌（版本号 +1），
        // 不换新的话当前这端也会被踢下线
        return ApiResponse.ok(authService.changePassword(currentUser.id(), form));
    }

    /**
     * POST /api/auth/refresh —— 用 refresh token 换一对新令牌。
     *
     * <p>这个接口必须在白名单里：调用它的时候 access token 往往**已经过期**了，
     * 如果还要求带有效 access，续期就永远失败。
     * 安全性由 refresh token 自身保证（类型必须是 refresh、版本号必须匹配、
     * 用户必须还存在），见 {@code AuthService.refresh}。
     */
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@RequestBody RefreshForm form) {
        return ApiResponse.ok(authService.refresh(form.getRefreshToken()));
    }
}
