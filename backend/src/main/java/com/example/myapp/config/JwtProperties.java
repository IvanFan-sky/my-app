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
 *
 * <h2>两种令牌</h2>
 * <ul>
 *   <li><b>access</b>（默认 15 分钟）：每个请求都带，有效期必须短 ——
 *       它一旦泄露，在没有版本号撤销之前就是个定时炸弹。</li>
 *   <li><b>refresh</b>（默认 7 天）：只在换 access 时用，不出现在业务请求里。
 *       有效期长是为了免去频繁登录，但**它同样受 token_version 约束**，
 *       改密码能一次性作废所有设备上的 refresh token。</li>
 * </ul>
 *
 * <p>为什么 access 是 15 分钟而不是原来的 120 分钟：短有效期 + refresh 续期
 * 是标准组合。access 短意味着泄露窗口小；用户不会因此频繁登录，因为前端会自动续期。
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** 签名密钥。为空表示未显式配置，由 JwtTokenProvider 回落到开发密钥 */
    private String secret;

    /**
     * access token 有效期（分钟），默认 15。
     *
     * <p>调大的代价：令牌泄露后的危险窗口变长。想「少刷新」应该调大 refresh 而不是它。
     */
    private long accessMinutes = 15;

    /**
     * refresh token 有效期（分钟），默认 7 天（10080）。
     *
     * <p>它的作用只是免去重新输密码，**不是**权限凭据 —— 换 access 时仍会校验
     * token_version 与用户是否存在，所以账号被改动后它会立刻失效。
     */
    private long refreshMinutes = 7 * 24 * 60;

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
        if (accessMinutes <= 0 || refreshMinutes <= 0) {
            throw new IllegalStateException(
                    "app.jwt.access-minutes 与 app.jwt.refresh-minutes 必须为正数，当前为 "
                            + accessMinutes + " / " + refreshMinutes);
        }
        if (refreshMinutes < accessMinutes) {
            // refresh 比 access 还短的话，续期机制毫无意义，属于配置写错
            throw new IllegalStateException(
                    "app.jwt.refresh-minutes(" + refreshMinutes + ") 不能小于 access-minutes("
                            + accessMinutes + ")：那样刷新出来的令牌立刻又过期。");
        }
        if (secret != null && secret.length() >= 32) {
            log.info("JWT 已使用外部密钥（长度 {}），access {} 分钟 / refresh {} 分钟",
                    secret.length(), accessMinutes, refreshMinutes);
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
