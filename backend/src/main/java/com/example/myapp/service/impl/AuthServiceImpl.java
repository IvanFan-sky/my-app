package com.example.myapp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.myapp.common.BizException;
import com.example.myapp.common.ErrorCode;
import com.example.myapp.dto.LoginForm;
import com.example.myapp.dto.LoginResponse;
import com.example.myapp.cache.UserCache;
import com.example.myapp.dto.RegisterForm;
import com.example.myapp.dto.UserVO;
import com.example.myapp.entity.User;
import com.example.myapp.security.JwtTokenProvider;
import com.example.myapp.service.AuthService;
import com.example.myapp.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
    private final UserCache userCache;

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
    public LoginResponse register(RegisterForm form) {
        String username = form.getUsername().trim();

        // 先查一次给出友好提示。注意这一步**不足以**保证唯一：
        //   1) 并发下两个请求可能同时通过检查，最终由唯一索引拦下；
        //   2) 更隐蔽的是逻辑删除 —— @TableLogic 让查询自动带上 deleted = 0，
        //      于是「已被逻辑删除的同名用户」查不出来，但它仍占着 username 的唯一索引
        //      （实测会抛 Duplicate entry）。这种情况下查重一定漏判。
        // 所以下面必须再兜住 DuplicateKeyException，只靠查重会让这种请求报 5000。
        if (userService.count(Wrappers.<User>lambdaQuery().eq(User::getUsername, username)) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "用户名已被占用，请换一个");
        }

        User user = new User();
        user.setUsername(username);
        // 邮箱留空按 null 处理，避免把空串写进库（空串和 NULL 在唯一性、查询上语义不同）
        user.setEmail(StringUtils.hasText(form.getEmail()) ? form.getEmail().trim() : null);
        // 复用与「新增用户」同一套密码处理（长度、UTF-8 字节数校验都在里面）
        userService.assignPassword(user, form.getPassword());

        try {
            userService.save(user);
            // 注册是 UserController 之外的第二条写路径，同样要失效缓存。
            // 新增时该 id 还不可能有缓存，但统一失效比依赖「新增不可能命中」的推理更稳：
            // 将来若在这个方法里加了别的写操作，不会因为漏掉这行而留下脏缓存。
            userCache.evict(user.getId());
        } catch (DuplicateKeyException e) {
            // 走到这里说明用户名被占：要么并发抢注，要么撞上已被逻辑删除的同名用户。
            // 对客户端而言两者的处理方式一样，都是「换个用户名」。
            log.info("注册失败：用户名唯一键冲突 username={}", username);
            throw new BizException(ErrorCode.CONFLICT, "用户名已被占用，请换一个");
        }

        // 注册即登录：直接签发令牌，省掉「注册完再登一次」这一步
        String token = tokenProvider.createToken(user.getId(), user.getUsername());
        log.info("注册成功 username={} userId={}", user.getUsername(), user.getId());
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
