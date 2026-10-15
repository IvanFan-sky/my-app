package com.example.myapp.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置，对应 application.yml 里的 app.jwt.*。
 *
 * <p>密钥来源优先级：环境变量 {@code APP_JWT_SECRET} / JVM 参数
 * {@code -Dapp.jwt.secret=...} &gt; 内置开发密钥。application.yml 里写的是
 * {@code ${APP_JWT_SECRET:}}，即环境变量缺失时该属性为空串，由
 * {@link com.example.myapp.security.JwtTokenProvider} 回落到开发密钥。
 *
 * <p>内置开发密钥是**故意**留的，理由：本地开发不必先配环境变量就能启动，
 * 少一类「启动即失败」的坑。代价是它必须被当作公开信息对待。
 * 所以这里在回落到开发密钥时打 WARN，并且提供
 * {@link #requireExternalSecret} —— 生产环境把它设为 true，
 * 忘记配密钥就会启动失败，而不是悄悄用公开密钥签发令牌。
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** 签名密钥。为空表示未显式配置，由 JwtTokenProvider 回落到开发密钥 */
    private String secret;

    /** 令牌有效期（分钟），默认 120 分钟 */
    private long expireMinutes = 120;

    /**
     * 是否强制要求密钥由外部（环境变量 / 启动参数）提供。
     * 生产环境应设为 true。默认 false，仅为降低本地开发的启动门槛。
     */
    private boolean requireExternalSecret = false;

    /**
     * 启动自检。
     *
     * <p>用 @PostConstruct 而不是在 JwtTokenProvider 里判断，是因为这里能在
     * 应用启动阶段就给出提示或失败，而不是等到第一次登录请求才暴露问题。
     * 抛 IllegalStateException 会被容器包装成启动失败，日志里能直接看到原因。
     */
    @PostConstruct
    void check() {
        if (secret != null && secret.length() >= 32) {
            log.info("JWT 已使用外部密钥（长度 {}），令牌有效期 {} 分钟", secret.length(), expireMinutes);
            return;
        }
        if (secret != null && !secret.isBlank()) {
            throw new IllegalStateException(
                    "app.jwt.secret 长度不足：" + secret.length() + " 字符。至少需要 32 字符（256 位），"
                            + "请换用更长的密钥，例如 openssl rand -base64 48 生成的值。");
        }
        if (requireExternalSecret) {
            throw new IllegalStateException(
                    "app.jwt.require-external-secret=true，但未提供密钥。"
                            + "请设置环境变量 APP_JWT_SECRET（至少 32 字符），或启动参数 -Dapp.jwt.secret=...");
        }
        log.warn("!! 正在使用内置开发密钥签发 JWT，该密钥是公开的，任何人都能伪造令牌。"
                + "仅限本地开发使用；部署前请设置环境变量 APP_JWT_SECRET（至少 32 字符），"
                + "并设置 app.jwt.require-external-secret=true 以防遗漏。");
    }
}
