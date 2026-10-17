package com.example.myapp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.myapp.common.BizException;
import com.example.myapp.common.ErrorCode;
import com.example.myapp.common.UserRole;
import com.example.myapp.dto.LoginForm;
import com.example.myapp.dto.LoginResponse;
import com.example.myapp.cache.LoginAttemptCache;
import com.example.myapp.common.AuditAction;
import com.example.myapp.cache.UserCache;
import com.example.myapp.dto.PasswordForm;
import com.example.myapp.dto.ProfileForm;
import com.example.myapp.dto.RegisterForm;
import com.example.myapp.dto.UserVO;
import com.example.myapp.entity.User;
import com.example.myapp.security.JwtTokenProvider;
import com.example.myapp.security.TokenInvalidException;
import com.example.myapp.service.AuditLogService;
import com.example.myapp.service.AuthService;
import com.example.myapp.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

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
    private final LoginAttemptCache loginAttemptCache;
    private final AuditLogService auditLogService;

    @Override
    public LoginResponse login(LoginForm form) {
        String username = form.getUsername().trim();
        String password = form.getPassword();

        // 先看是否已被临时锁定。放在最前面是有意的：
        // 被锁定时不再做哈希校验，既省 CPU（这正是限流的意义），
        // 也避免攻击者靠响应时间继续探测。
        long lockedMinutes = loginAttemptCache.lockRemainingMinutes(username);
        if (lockedMinutes > 0) {
            log.warn("登录被拒：尝试次数过多，账号处于临时锁定 username={} 剩余={}分钟",
                    username, lockedMinutes);
            auditLogService.record(AuditAction.LOGIN_FAIL, null, username, null, null,
                    Map.of("reason", "账号锁定", "lockedMinutes", lockedMinutes), false);
            // 4290 而不是 4010：前端见到 4010 会清登录态并跳登录页，
            // 而这里用户只是被限流，跳登录页等于让他更困惑
            throw new BizException(ErrorCode.TOO_MANY_ATTEMPTS,
                    "登录失败次数过多，请 " + lockedMinutes + " 分钟后再试");
        }

        // 用一条 select 查库：虽然多查一列 password_hash，但比先查存在性再查密码少一次往返
        User user = userService.getOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, username), false);

        // 注意：下面所有失败分支都走同一个 unauthorized()，文案与错误码完全一致
        if (user == null) {
            // 空跑一次哈希校验，把响应时间拉到和「密码错误」一致
            passwordEncoder.matches(password, DUMMY_BCRYPT_HASH);
            log.info("登录失败：用户名不存在 username={}", username);
            // 不存在的用户名也计数：否则「有计数 = 账号存在」本身就成了枚举信号
            loginAttemptCache.recordFailure(username);
            auditLogService.record(AuditAction.LOGIN_FAIL, null, username, null, null,
                    Map.of("reason", "用户不存在"), false);
            throw unauthorized();
        }

        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            log.info("登录失败：密码错误 username={} userId={}", username, user.getId());
            loginAttemptCache.recordFailure(username);
            // 这个分支用户是真实存在的，actor 记成他本人：
            // 审计里「某人连续输错密码」比「一个匿名操作」有用得多
            auditLogService.record(AuditAction.LOGIN_FAIL, user.getId(), username,
                    user.getId(), user.getUsername(), Map.of("reason", "密码错误"), false);
            throw unauthorized();
        }

        // 「连续失败」语义：成功一次就把计数清零，否则用户正常登录过、
        // 隔几天再输错两次就被锁，会显得莫名其妙
        loginAttemptCache.clear(username);
        log.info("登录成功 username={} userId={}", user.getUsername(), user.getId());
        auditLogService.record(AuditAction.LOGIN, user.getId(), user.getUsername(),
                user.getId(), user.getUsername(), null, true);
        return issueTokens(user);
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
        // 角色显式给默认值。数据库列也有 DEFAULT 'USER'，但**返回给客户端的 VO
        // 是由内存实体构造的**，不显式赋值的话响应里的 role 会是 null（被新加的测试抓到）。
        // 注册接口的安全性依赖「新用户不是管理员」，这个字段不能只靠数据库兜底。
        user.setRole(UserRole.DEFAULT_ROLE);
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

        // 注册即登录：直接签发令牌对，省掉「注册完再登一次」这一步。
        // 先重新读一次再签发：created_at / updated_at 由数据库生成，
        // 刚 save 完的实体上还是 null，直接返回会让前端拿到空时间
        // （与 UserController.create 是同一个问题）
        User fresh = userService.getById(user.getId());
        log.info("注册成功 username={} userId={}", user.getUsername(), user.getId());
        auditLogService.record(AuditAction.REGISTER, fresh.getId(), fresh.getUsername(),
                fresh.getId(), fresh.getUsername(), null, true);
        return issueTokens(fresh);
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

    @Override
    public UserVO updateProfile(Long userId, ProfileForm form) {
        User user = userService.getById(userId);
        if (user == null) {
            // 令牌有效但用户已删除：与 getCurrentUser 保持同一语义
            log.warn("修改资料失败：用户不存在或已删除 userId={}", userId);
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }

        // 留空一律写 null，而不是空串：
        // 「没填邮箱」和「邮箱是空字符串」在展示与后续唯一性判断上语义不同，
        // 混用会让前端不得不到处判断空串。
        //
        // 这里**必须**用 overwriteEditable：updateById 会跳过 null 字段，
        // 于是「清空显示名」会变成「不改显示名」，用户点清空后值还在（实测踩到）。
        userService.overwriteEditable(userId,
                textOrNull(form.getNickname()),
                textOrNull(form.getEmail()),
                textOrNull(form.getBio()),
                form.getAge(),
                null);
        // 缓存里存的是 UserVO（含 nickname/role/updatedAt），必须失效，
        // 否则资料页改完、刷新后读到的还是旧值
        userCache.evict(userId);

        // 重新读一次以拿到数据库维护的 updated_at。
        // 直接用内存里的 user 也能返回，但 updatedAt 会是旧值 —— 前端会显示成「没保存成功」。
        User fresh = userService.getById(userId);
        log.info("资料已更新 userId={} username={}", userId, user.getUsername());

        // 只记「改了哪些字段名」，不记值：邮箱等属于个人信息，日志是长期留存的，
        // 把值抄进去等于给自己造了一份影子用户表。
        Map<String, Object> changed = new java.util.LinkedHashMap<>();
        if (!java.util.Objects.equals(user.getNickname(), fresh.getNickname())) {
            changed.put("nickname", "changed");
        }
        if (!java.util.Objects.equals(user.getEmail(), fresh.getEmail())) {
            changed.put("email", "changed");
        }
        if (!java.util.Objects.equals(user.getBio(), fresh.getBio())) {
            changed.put("bio", "changed");
        }
        if (!java.util.Objects.equals(user.getAge(), fresh.getAge())) {
            changed.put("age", "changed");
        }
        auditLogService.record(AuditAction.UPDATE_PROFILE, userId, user.getUsername(),
                userId, user.getUsername(), changed, true);

        return UserVO.from(fresh);
    }

    @Override
    public LoginResponse changePassword(Long userId, PasswordForm form) {
        User user = userService.getById(userId);
        if (user == null) {
            log.warn("修改密码失败：用户不存在或已删除 userId={}", userId);
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }

        // 必须验原密码：否则令牌一旦泄露（或未登出共用电脑），对方可以直接改密码锁死账号
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(form.getOldPassword(), user.getPasswordHash())) {
            log.info("修改密码失败：原密码不正确 userId={}", userId);
            // 4000 而不是 4010：见 AuthService.changePassword 的注释
            throw new BizException(ErrorCode.PARAM_INVALID, "原密码不正确");
        }

        // 新旧相同直接拒绝。不校验也能过，但用户会觉得「改了却好像没变」，
        // 而且白白多一次哈希写入。
        if (passwordEncoder.matches(form.getNewPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "新密码不能与原密码相同");
        }

        // 复用同一套密码校验（最少 6 位、UTF-8 不超过 72 字节）。
        // 只改密码，其余可编辑字段按原值回写，避免被覆盖成 null
        userService.overwriteEditable(user.getId(),
                user.getNickname(), user.getEmail(), user.getBio(), user.getAge(),
                userService.encodePassword(form.getNewPassword()));
        // overwriteEditable 在写入新密码的同一条 SQL 里把 token_version +1，
        // 所以**所有已签发的令牌（含 refresh）此刻起全部失效** ——
        // 这正是「改密码能把别的设备踢下线」的实现方式。
        userCache.evict(userId);

        // 重新读：一是拿新版本号，二是让返回的 UserVO 带最新的 updatedAt
        User fresh = userService.getById(userId);
        log.info("密码已修改，已作废该用户全部旧令牌 userId={} username={}", userId, user.getUsername());
        // 只记「改过密码」这件事，绝不记新旧密码值
        auditLogService.record(AuditAction.CHANGE_PASSWORD, userId, user.getUsername(),
                userId, user.getUsername(), null, true);

        // **必须返回新的令牌对**：当前这端的旧令牌也已被作废，
        // 不换新的话用户改完密码立刻被踢回登录页 —— 那不是他想要的。
        return issueTokens(fresh);
    }

    @Override
    public LoginResponse refresh(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }

        JwtTokenProvider.ParsedToken parsed;
        try {
            parsed = tokenProvider.parse(refreshToken.trim());
        } catch (TokenInvalidException e) {
            // 不区分「过期」「签名错」「格式错」：对外都是同一句话，
            // 否则等于告诉攻击者他手里的令牌是哪种状态
            log.debug("刷新令牌无效: {}", e.getMessage());
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }

        // 类型必须是 refresh。不校验的话，access 也能拿来换新令牌，
        // 「access 只有 15 分钟」这条防线自己就没了。
        if (!parsed.isRefresh()) {
            log.info("试图用非 refresh 令牌刷新 userId={}", parsed.userId());
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }

        User user = userService.getById(parsed.userId());
        if (user == null) {
            log.info("刷新令牌指向的用户已不存在 userId={}", parsed.userId());
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }

        // 版本号比对：改过密码/角色的 refresh 必须失效，否则「撤销」不彻底
        int currentVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        if (parsed.tokenVersion() != currentVersion) {
            log.info("刷新令牌版本已过期 userId={} 令牌={} 当前={}",
                    parsed.userId(), parsed.tokenVersion(), currentVersion);
            throw new BizException(ErrorCode.UNAUTHORIZED, "密码或权限已变更，请重新登录");
        }

        log.debug("刷新令牌成功 userId={}", parsed.userId());
        // 轮换：新的 access + 新的 refresh，旧的 refresh 到期自然失效
        return issueTokens(user);
    }

    /**
     * 签发一对令牌并组装登录响应。
     *
     * <p>access 与 refresh 用**同一个 token_version**，两者一起被撤销。
     * 前端拿 refreshToken 去 /api/auth/refresh 换来新的一对。
     */
    private LoginResponse issueTokens(User user) {
        int version = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        String access = tokenProvider.createAccessToken(user.getId(), user.getUsername(), version);
        String refresh = tokenProvider.createRefreshToken(user.getId(), user.getUsername(), version);
        // 参数顺序与 LoginResponse 的字段顺序一致：token, refreshToken, tokenType, expiresIn, user
        return new LoginResponse(access, refresh, "Bearer",
                tokenProvider.getAccessSeconds(), UserVO.from(user));
    }

    /** 去掉首尾空白；空白一律视为未填写（写 null 而不是空串） */
    private static String textOrNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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
