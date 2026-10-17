package com.example.myapp.api;

import com.example.myapp.cache.LoginAttemptCache;
import com.example.myapp.common.ErrorCode;
import com.example.myapp.common.UserRole;
import com.example.myapp.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 登录失败次数限制。
 *
 * <p>为什么值得单独一组测试：限流是**唯一一个会让正常用户被挡在门外**的功能。
 * 阈值算错、计数不清零、TTL 没设，都会表现为「密码明明是对的却登不进去」，
 * 而这种故障在手工测试里很难复现（要连续错 N 次）。
 */
class LoginLockoutTest extends ApiTestSupport {

    @Autowired
    private LoginAttemptCache loginAttemptCache;

    // 注意：每个测试造的失败计数由基类 cleanUpTestData 统一清理
    // （按 createdUsernames 删 my-app:login:fail:*），这里不需要再写 @AfterEach。

    @Test
    @DisplayName("连续失败达到阈值后被临时锁定，返回 4290")
    void locksAfterMaxAttempts() throws Exception {
        String username = newUsername();
        createUser(username, TEST_PASSWORD, UserRole.USER);

        // 阈值配的是 5，先错 5 次
        for (int i = 0; i < 5; i++) {
            assertStatus(postJson("/api/auth/login", null,
                    Map.of("username", username, "password", "wrong-" + i)), 200, 4010);
        }

        // 第 6 次即使密码是对的也必须被拒：锁定不看这次密码对不对
        var locked = postJson("/api/auth/login", null,
                Map.of("username", username, "password", TEST_PASSWORD));
        assertStatus(locked, 200, ErrorCode.TOO_MANY_ATTEMPTS);
        assertThat(jsonString(locked, "/message"))
                .as("提示里要给出等待时间，否则用户不知道该怎么办")
                .contains("分钟");

        assertThat(loginAttemptCache.lockRemainingSeconds(username))
                .as("Redis 里应当有剩余锁定时间")
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("未达到阈值的失败不会锁定")
    void doesNotLockBelowThreshold() throws Exception {
        String username = newUsername();
        createUser(username, TEST_PASSWORD, UserRole.USER);

        for (int i = 0; i < 4; i++) {
            assertStatus(postJson("/api/auth/login", null,
                    Map.of("username", username, "password", "wrong-" + i)), 200, 4010);
        }

        // 第 5 次还不到阈值（阈值 5 表示「失败 5 次之后才锁」），正确密码应当能进
        assertThat(login(username, TEST_PASSWORD)).isNotBlank();
    }

    @Test
    @DisplayName("登录成功会把失败计数清零（连续失败语义）")
    void successResetsCounter() throws Exception {
        String username = newUsername();
        createUser(username, TEST_PASSWORD, UserRole.USER);

        // 错 4 次（还没到阈值）
        for (int i = 0; i < 4; i++) {
            assertStatus(postJson("/api/auth/login", null,
                    Map.of("username", username, "password", "wrong-" + i)), 200, 4010);
        }
        // 成功一次
        assertThat(login(username, TEST_PASSWORD)).isNotBlank();
        assertThat(loginAttemptCache.lockRemainingSeconds(username)).isZero();

        // 再错 4 次也不该锁 —— 如果计数没清零，这里累计就是 8 次，必然被锁
        for (int i = 0; i < 4; i++) {
            assertStatus(postJson("/api/auth/login", null,
                    Map.of("username", username, "password", "wrong-again-" + i)), 200, 4010);
        }
        assertThat(login(username, TEST_PASSWORD)).isNotBlank();
    }

    @Test
    @DisplayName("不存在的用户名同样计数，避免「有计数=账号存在」成为枚举信号")
    void unknownUsernameIsCountedToo() throws Exception {
        String username = newUsername(); // 只登记名字，不建用户

        for (int i = 0; i < 5; i++) {
            assertStatus(postJson("/api/auth/login", null,
                    Map.of("username", username, "password", "wrong-" + i)), 200, 4010);
        }

        assertStatus(postJson("/api/auth/login", null,
                Map.of("username", username, "password", "whatever")), 200, ErrorCode.TOO_MANY_ATTEMPTS);
    }
}
