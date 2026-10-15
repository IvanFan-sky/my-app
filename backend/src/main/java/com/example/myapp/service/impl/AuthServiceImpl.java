package com.example.myapp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.myapp.common.BizException;
import com.example.myapp.common.ErrorCode;
import com.example.myapp.dto.LoginForm;
import com.example.myapp.dto.LoginResponse;
import com.example.myapp.dto.UserVO;
import com.example.myapp.entity.User;
import com.example.myapp.security.JwtTokenProvider;
import com.example.myapp.service.AuthService;
import com.example.myapp.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /**
     * 用户名不存在时用来「陪着算一次」的假哈希，防止通过响应时间枚举用户名。
     *
     * <p>不写这段会怎样：用户不存在时直接抛异常返回，耗时约 1ms；密码错误时要跑一次
     * BCrypt 校验，耗时约 50–100ms。攻击者据此就能可靠区分「用户名是否存在」，
     * 于是前面「两种情况返回同样文案」的努力全白费 —— 文案一样，时间不一样。
     *
     * <p>这个哈希对应一个随机口令，永远不会有人用它登录成功；它只用来消耗时间。
     */
    private static final String DUMMY_BCRYPT_HASH =
            "$2a$10$oNCrKMnqHbly9AoZT2nM6.TKfzYXDJDr8Ck1y/ddkh6MrlKiosctm";

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Override
    public LoginResponse login(LoginForm form) {
        String username = form.getUsername().trim();
        String password = form.getPassword();

        // 用一条 select 查库：虽然多查一列 password_hash，但比先查存在性再查密码少一次往返
        User user = userService.getOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, username), false);

        // 注意：下面所有失败分支都走同一个 unauthorized()，文案与错误码完全一致
        if (user == null) {
            // 空跑一次哈希校验，把响应时间拉到和「密码错误」一致
            passwordEncoder.matches(password, DUMMY_BCRYPT_HASH);
            log.info("登录失败：用户名不存在 username={}", username);
            throw unauthorized();
        }

        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            log.info("登录失败：密码错误 username={} userId={}", username, user.getId());
            throw unauthorized();
        }

        String token = tokenProvider.createToken(user.getId(), user.getUsername());
        log.info("登录成功 username={} userId={}", user.getUsername(), user.getId());
        return new LoginResponse(token, "Bearer", tokenProvider.getExpireSeconds(), UserVO.from(user));
    }

    @Override
    public UserVO getCurrentUser(Long userId, String authenticatedName) {
        // 这里必须真查库：令牌在有效期内一直可用，但用户可能已经被删或改过名
        User user = userService.getById(userId);
        if (user == null) {
            log.warn("令牌有效但用户已不存在或已被删除 userId={} tokenUsername={}", userId, authenticatedName);
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }
        return UserVO.from(user);
    }

    /**
     * 统一的登录失败异常。
     *
     * <p>「用户不存在」与「密码错误」共用这一个方法，是为了从代码结构上保证两者
     * 不可能被改得不一致 —— 分两处写文案，早晚有人只改其中一处。
     */
    private static BizException unauthorized() {
        return new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
    }
}
