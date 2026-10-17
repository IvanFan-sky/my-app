package com.example.myapp.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 登录失败次数限制：连续失败达到阈值后临时锁定该用户名。
 *
 * <p><b>为什么必须有</b>：有 JWT 不等于有防护。没有这一层，攻击者可以无限次
 * 猜密码，账号安全就只剩「密码本身够不够强」这一条防线。BCrypt 慢一点能减缓，
 * 但拦不住持续尝试。
 *
 * <p><b>key 为什么按用户名而不是 IP</b>：
 * <ul>
 *   <li>按 IP 会被 NAT / 代理后的正常用户误伤（一个办公室共用一个出口 IP）；</li>
 *   <li>按用户名才能表达「这个账号正被攻击」。</li>
 * </ul>
 *
 * <p><b>已知取舍：按用户名锁定意味着攻击者可以故意输错来锁死别人的账号（DoS）。</b>
 * 缓解办法是「锁定时间短（默认 15 分钟）+ 到期自动解锁 + 登录成功清零」，
 * 并且**不会因为锁定而延长到永久**。面向公网的产品建议再叠加
 * IP+用户名双维度计数或验证码；本项目按「管理后台、内网/小规模」定位先做到这一步。
 *
 * <p><b>Redis 故障时 fail-open</b>：所有操作都包了 try/catch。
 * Redis 挂掉时**放行登录**并打 WARN，而不是把所有人挡在门外 ——
 * 缓存是加速与防护手段，不该成为单点故障。这与 UserCache 的策略一致。
 */
@Slf4j
@Component
public class LoginAttemptCache {

    /** 与 UserCache 一致的前缀风格，便于在 Redis 里一眼看出归属 */
    private static final String KEY_PREFIX = "my-app:login:fail:";

    private final StringRedisTemplate redis;

    /** 连续失败达到这个次数就锁定 */
    private final int maxAttempts;

    /** 锁定窗口（分钟）。每次失败都刷新 TTL，即「从最后一次失败算起」 */
    private final long lockMinutes;

    public LoginAttemptCache(StringRedisTemplate redis,
                             @Value("${app.security.login.max-attempts:5}") int maxAttempts,
                             @Value("${app.security.login.lock-minutes:15}") long lockMinutes) {
        this.redis = redis;
        this.maxAttempts = maxAttempts;
        this.lockMinutes = lockMinutes;
    }

    /**
     * 还要被锁多少秒。
     *
     * @return 0 表示未锁定；大于 0 表示剩余锁定秒数
     */
    public long lockRemainingSeconds(String username) {
        try {
            String value = redis.opsForValue().get(key(username));
            if (value == null) {
                return 0;
            }
            long failures = parseOrZero(value);
            if (failures < maxAttempts) {
                return 0;
            }
            Long ttl = redis.getExpire(key(username), TimeUnit.SECONDS);
            if (ttl == null || ttl <= 0) {
                // 计数还在但没 TTL（理论不该出现，除非 key 被手工写入），
                // 按完整窗口处理，避免出现「永久锁定」
                return lockMinutes * 60;
            }
            return ttl;
        } catch (Exception e) {
            // fail-open：Redis 不可用时不阻断登录
            log.warn("读取登录失败计数出错，本次放行（fail-open）: {}", e.getMessage());
            return 0;
        }
    }

    /** 记一次失败。达到阈值后由 {@link #lockRemainingSeconds} 判定为锁定 */
    public void recordFailure(String username) {
        try {
            String key = key(username);
            Long failures = redis.opsForValue().increment(key);
            // 每次都刷新 TTL：滑动窗口，从最后一次失败重新计时。
            // 不刷新的话，攻击者可以在第 N 次失败后一直等到 key 过期再继续，
            // 而正常用户输错几次后隔几分钟再试却能立刻解锁。
            redis.expire(key, lockMinutes, TimeUnit.MINUTES);
            if (failures != null && failures == maxAttempts) {
                log.warn("登录失败次数达到阈值，临时锁定 username={} 次数={} 时长={}分钟",
                        username, failures, lockMinutes);
            }
        } catch (Exception e) {
            // 计数失败不影响登录流程本身：这里不能把异常抛出去
            log.warn("记录登录失败次数出错，忽略: {}", e.getMessage());
        }
    }

    /** 登录成功后清零。失败次数是「连续」语义，成功一次就该重新计 */
    public void clear(String username) {
        try {
            redis.delete(key(username));
        } catch (Exception e) {
            log.warn("清除登录失败计数出错，忽略: {}", e.getMessage());
        }
    }

    /** 剩余锁定分钟数，用于拼提示文案（向上取整，避免显示「0 分钟」） */
    public long lockRemainingMinutes(String username) {
        long seconds = lockRemainingSeconds(username);
        return seconds <= 0 ? 0 : (seconds + 59) / 60;
    }

    private static String key(String username) {
        // username 在调用前已 trim；这里不再做额外规范化 ——
        // 大小写不同的用户名是不同账号（登录本身就是大小写敏感的精确匹配）
        return KEY_PREFIX + username;
    }

    private static long parseOrZero(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
