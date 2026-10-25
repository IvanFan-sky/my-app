package com.example.myapp.controller;

import com.example.myapp.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查：用来验证「服务起来了 + MySQL / Redis 都连得上」。
 *
 * <p>GET /api/health
 *
 * <p><b>为什么两个下游都要探</b>：只探 Redis 的话，数据库挂掉时接口仍会返回
 * {@code redis=pong}，工作台显示「一切正常」，而所有业务接口都在报错 ——
 * 健康检查的价值恰恰是提前发现问题，漏掉主要故障源等于没做。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("app", "my-app");
        body.put("time", LocalDateTime.now());

        // 真连一次 Redis 再写读，而不是只看配置是否存在
        boolean redisUp = false;
        try {
            String key = "my-app:health:ping";
            redisTemplate.opsForValue().set(key, "pong", Duration.ofMinutes(1));
            String pong = redisTemplate.opsForValue().get(key);
            body.put("redis", pong);
            redisUp = "pong".equals(pong);
        } catch (Exception e) {
            body.put("redis", "DOWN: " + e.getClass().getSimpleName());
        }

        // 数据库同样要真探一次：SELECT 1 会走连接池，连接池取不到连接也会在这里暴露
        boolean mysqlUp = false;
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            body.put("mysql", "pong");
            mysqlUp = true;
        } catch (Exception e) {
            body.put("mysql", "DOWN: " + e.getClass().getSimpleName());
        }

        // 汇总位：调用方只想判断「整体能不能用」时读这一个字段就够了
        body.put("healthy", redisUp && mysqlUp);
        return ApiResponse.ok(body);
    }
}
