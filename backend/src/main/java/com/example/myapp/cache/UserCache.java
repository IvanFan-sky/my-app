package com.example.myapp.cache;

import com.example.myapp.dto.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用户信息的 Redis 缓存（按主键缓存）。
 *
 * <p><b>缓存什么</b>：{@link UserVO}，不是 {@code User} 实体。
 * 实体带着 {@code passwordHash}，而 Redis 通常被当作低安全等级的存储，
 * 把密码哈希放进去等于平白扩大泄露面。UserVO 恰好是「对外可见的那部分」，语义最干净。
 *
 * <p><b>只按 id 缓存，不缓存分页列表</b>。列表的缓存 key 要拼上关键字、页码、每页条数，
 * 组合会爆炸；更麻烦的是任何一条用户变动都得让整片列表失效，稍有遗漏就返回脏数据。
 * 按 id 缓存是唯一能保证正确性的粒度。
 *
 * <p><b>Redis 故障绝不能影响业务</b>。所有读写都包在 try/catch 里，失败只记 WARN 并
 * 当作「缓存未命中」返回，由调用方回源数据库。缓存是纯优化项，因为 Redis 抖动就让
 * 用户管理整片 500 是本末倒置。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserCache {

    /** key 前缀，统一带项目名，避免和同库其它应用撞 key */
    private static final String KEY_PREFIX = "my-app:user:";

    /**
     * 过期时间 10 分钟。
     *
     * <p>这是「回源数据的副本」，不是权威数据源，所以设 TTL 而不是永久缓存：
     * 万一将来新增了某条失效路径漏掉（比如直接在数据库里改了数据），
     * 最多脏 10 分钟就会自愈，而不是一直错下去。
     */
    private static final Duration TTL = Duration.ofMinutes(10);

    private final RedisTemplate<String, Object> redisTemplate;

    /** 命中/未命中计数。用于 /api/users/stats 展示缓存是否真的在起作用 */
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    /** 取缓存。任何异常（含 Redis 不可用）都返回空，由调用方回源 */
    public Optional<UserVO> get(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        try {
            Object cached = redisTemplate.opsForValue().get(key(id));
            if (cached instanceof UserVO vo) {
                hits.incrementAndGet();
                return Optional.of(vo);
            }
            if (cached != null) {
                // 缓存里确实有值，但反序列化出来不是 UserVO（通常是 Map）。
                // 这说明 RedisConfig 少了 activateDefaultTyping —— 那时 JSON 里没有
                // "@class"，Jackson 只能还原成 LinkedHashMap，于是**缓存永远命中不了**
                // 且**不报任何错**，命中率一直是 0%，极难排查。
                // 所以这里显式打 ERROR：把这种「静默失效」变成一眼可见。
                log.error("缓存值类型不符，实际={} 期望=UserVO。"
                                + "多半是 RedisConfig 未开启 Jackson 默认类型信息，缓存将永远无法命中 id={}",
                        cached.getClass().getName(), id);
            }
            misses.incrementAndGet();
            return Optional.empty();
        } catch (Exception e) {
            // 注意：这里**不**计入 miss。Redis 故障不代表「缓存未命中」，
            // 把它算进命中率会让指标失真，看不出真实效果。
            log.warn("读取用户缓存失败，将回源数据库 id={} 原因={}", id, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** 写缓存。失败只记 WARN —— 数据已经写进数据库了，缓存没写上是可接受的降级 */
    public void put(UserVO vo) {
        if (vo == null || vo.getId() == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(vo.getId()), vo, TTL);
        } catch (Exception e) {
            log.warn("写入用户缓存失败 id={} 原因={}", vo.getId(), e.getClass().getSimpleName());
        }
    }

    /**
     * 失效某个用户的缓存。**所有写操作（新增/修改/删除）都必须调它。**
     *
     * <p>删除这条尤其不能省：如果逻辑删除后缓存还在，{@code AuthInterceptor}
     * 会从缓存里读到「这个用户还存在」，于是**已删除用户手里的旧令牌在有效期内仍能访问接口**。
     * 这不是缓存脏读的小问题，是权限漏洞。
     */
    public void evict(Long id) {
        if (id == null) {
            return;
        }
        try {
            redisTemplate.delete(key(id));
        } catch (Exception e) {
            log.warn("失效用户缓存失败 id={} 原因={}", id, e.getClass().getSimpleName());
        }
    }

    /** 缓存命中率（0~1）。总请求数为 0 时返回 0，避免除零 */
    public double hitRate() {
        long h = hits.get();
        long m = misses.get();
        long total = h + m;
        return total == 0 ? 0d : (double) h / total;
    }

    public long hitCount() {
        return hits.get();
    }

    public long missCount() {
        return misses.get();
    }

    private static String key(Long id) {
        return KEY_PREFIX + id;
    }
}
