package com.example.myapp.security;

import com.example.myapp.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 的签发与解析。
 *
 * <p>这是一个纯技术组件：不感知 HTTP（不碰 HttpServletRequest），也不查数据库。
 * 校验「用户是否还存在 / 版本号是否匹配」是拦截器与 AuthService 的职责，别混进来。
 *
 * <h2>两种令牌，两个必须校验的声明</h2>
 * <ul>
 *   <li>{@code typ}：{@code access} 或 {@code refresh}。
 *       <b>必须校验</b> —— 否则拿 refresh token 当 access 用就能绕过短有效期，
 *       那 access 设 15 分钟就毫无意义了。</li>
 *   <li>{@code ver}：签发时的 token_version。
 *       拦截器拿它和库里的当前值比对，实现「改密码后旧令牌立即失效」。</li>
 * </ul>
 */
@Component
public class JwtTokenProvider {

    /**
     * 内置开发密钥。**这是公开的**，只为降低本地启动门槛。
     *
     * <p>长度必须 ≥ 32 字节：jjwt 0.12 起 {@code signWith(key)} 会按密钥长度自动选算法，
     * 短于 256 位会直接抛 WeakKeyException。本机实测这个 51 字节的密钥选出的是
     * <b>HS384</b>（不是 HS256）—— 这是预期行为，不是配置错误。
     * 生产环境必须用环境变量 APP_JWT_SECRET 覆盖，见 JwtProperties。
     */
    private static final String DEV_SECRET = "my-app-dev-only-secret-key-do-not-use-in-production";

    /** 自定义 claim：用户主键。用 string 存，避免 JSON 数字在不同语言里被解析成浮点 */
    private static final String CLAIM_USER_ID = "uid";

    /** 自定义 claim：令牌类型（access / refresh） */
    private static final String CLAIM_TYPE = "typ";

    /** 自定义 claim：令牌版本号 */
    private static final String CLAIM_VERSION = "ver";

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        String configured = properties.getSecret();
        String actual = (configured == null || configured.isBlank()) ? DEV_SECRET : configured;
        // JwtProperties.check() 已经保证密钥长度，这里直接构造即可
        this.secretKey = Keys.hmacShaKeyFor(actual.getBytes(StandardCharsets.UTF_8));
    }

    /** 签发 access token（短有效期，随每个请求发送） */
    public String createAccessToken(Long userId, String username, Integer tokenVersion) {
        return create(userId, username, tokenVersion, TYPE_ACCESS,
                Duration.ofMinutes(properties.getAccessMinutes()));
    }

    /** 签发 refresh token（长有效期，只在换 access 时提交） */
    public String createRefreshToken(Long userId, String username, Integer tokenVersion) {
        return create(userId, username, tokenVersion, TYPE_REFRESH,
                Duration.ofMinutes(properties.getRefreshMinutes()));
    }

    private String create(Long userId, String username, Integer tokenVersion, String type,
                          Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                // jti：每个令牌一个唯一 id。
                // 不加的话，同一秒内为同一用户签发的两个令牌**字节完全相同**
                // （声明一样、时间戳精度到秒），于是「刷新时轮换令牌」变成空操作 ——
                // 旧 refresh 与新 refresh 一模一样，等于没轮换。
                // 顺带为将来做「按令牌撤销」留下了抓手。
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claim(CLAIM_USER_ID, String.valueOf(userId))
                .claim(CLAIM_TYPE, type)
                // 版本号为 null 时写 0，与服务端「缺省视为 0」的判断保持一致，
                // 避免老数据（V3 之前创建、token_version 为 null 的行）出现误判
                .claim(CLAIM_VERSION, tokenVersion == null ? 0 : tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并校验令牌（签名 + 有效期）。
     *
     * <p>把 jjwt 的受检异常统一转成 {@link TokenInvalidException}，
     * 调用方因此不用 import jjwt 的异常类型，也就不会不小心把
     * 「令牌过期」当成「服务器错误」处理。
     *
     * <p>注意：这里**不**校验 typ 是 access 还是 refresh，因为两种令牌都要被解析；
     * 调用方必须自己检查 {@link ParsedToken#type()}。拦截器要求 access、
     * 刷新接口要求 refresh —— 混淆这两者是这类实现最常见的漏洞。
     *
     * @throws TokenInvalidException 签名不合法、已过期、格式错误、缺少 uid claim
     */
    public ParsedToken parse(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            // 不把原始异常消息返回给前端：它可能泄露实现细节，对攻击者反而是提示
            throw new TokenInvalidException("令牌无效或已过期", e);
        }

        String uid = claims.get(CLAIM_USER_ID, String.class);
        if (uid == null || uid.isBlank()) {
            throw new TokenInvalidException("令牌缺少 uid 声明");
        }

        String type = claims.get(CLAIM_TYPE, String.class);
        if (!TYPE_ACCESS.equals(type) && !TYPE_REFRESH.equals(type)) {
            // 没有 typ 的令牌一律拒绝：它只可能来自旧版本或伪造，
            // 放行的话「拿 refresh 当 access」这条攻击路径就开着
            throw new TokenInvalidException("令牌缺少类型声明");
        }

        Integer version = claims.get(CLAIM_VERSION, Integer.class);

        try {
            return new ParsedToken(
                    Long.valueOf(uid),
                    claims.getSubject(),
                    version == null ? 0 : version,
                    type,
                    claims.getExpiration() == null ? null : claims.getExpiration().toInstant());
        } catch (NumberFormatException e) {
            throw new TokenInvalidException("令牌中的 uid 不是合法数字", e);
        }
    }

    /** access token 有效期（秒），用于登录响应里的 expiresIn */
    public long getAccessSeconds() {
        return Duration.ofMinutes(properties.getAccessMinutes()).toSeconds();
    }

    /** refresh token 有效期（秒），用于前端判断何时该主动续期 */
    public long getRefreshSeconds() {
        return Duration.ofMinutes(properties.getRefreshMinutes()).toSeconds();
    }

    /** 解析结果：只暴露业务需要的部分，不把 jjwt 的 Claims 泄漏到上层 */
    public record ParsedToken(Long userId, String username, Integer tokenVersion, String type,
                              Instant expiresAt) {

        public boolean isAccess() {
            return TYPE_ACCESS.equals(type);
        }

        public boolean isRefresh() {
            return TYPE_REFRESH.equals(type);
        }
    }
}
