package com.example.myapp.controller;

import com.example.myapp.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查：用来验证「服务起来了 + Redis 连得上」。
 *
 * <p>GET /api/health
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    private final StringRedisTemplate redisTemplate;

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("app", "my-app");
        body.put("time", LocalDateTime.now());

        // 真连一次 Redis 再写读，而不是只看配置是否存在
        try {
            String key = "my-app:health:ping";
            redisTemplate.opsForValue().set(key, "pong", Duration.ofMinutes(1));
            body.put("redis", redisTemplate.opsForValue().get(key));
        } catch (Exception e) {
            body.put("redis", "DOWN: " + e.getClass().getSimpleName());
        }
        return ApiResponse.ok(body);
    }
}
