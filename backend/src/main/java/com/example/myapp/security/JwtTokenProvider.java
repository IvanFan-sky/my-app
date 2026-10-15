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

/**
 * JWT 的签发与解析。
 *
 * <p>这是一个纯技术组件：不感知 HTTP（不碰 HttpServletRequest），也不查数据库。
 * 校验「用户是否还存在 / 是否被删除」是 AuthService 的职责，别混进来。
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

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        String configured = properties.getSecret();
        String actual = (configured == null || configured.isBlank()) ? DEV_SECRET : configured;
        // JwtProperties.check() 已经保证密钥长度，这里直接构造即可
        this.secretKey = Keys.hmacShaKeyFor(actual.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发令牌。
     *
     * @param userId   用户主键，写入 uid claim
     * @param username 用户名，写入标准 sub（subject）claim
     */
    public String createToken(Long userId, String username) {
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofMinutes(properties.getExpireMinutes()));
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_USER_ID, String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
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
        try {
            return new ParsedToken(Long.valueOf(uid), claims.getSubject(),
                    claims.getExpiration() == null ? null : claims.getExpiration().toInstant());
        } catch (NumberFormatException e) {
            throw new TokenInvalidException("令牌中的 uid 不是合法数字", e);
        }
    }

    /** 令牌有效期（秒），用于登录响应里的 expiresIn */
    public long getExpireSeconds() {
        return Duration.ofMinutes(properties.getExpireMinutes()).toSeconds();
    }

    /** 解析结果：只暴露业务需要的部分，不把 jjwt 的 Claims 泄漏到上层 */
    public record ParsedToken(Long userId, String username, Instant expiresAt) {
    }
}
