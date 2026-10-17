package com.example.myapp.api;

import com.example.myapp.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 认证流程：登录、注册、当前用户。
 *
 * <p>这些断言原先写在 PowerShell 脚本里手工跑，现已转为 JUnit 并进 CI。
 * 理由：手工脚本没人跑就等于没有，而这里的东西（尤其「两种登录失败
 * 返回完全相同文案」）一旦被改坏是**不会报错**的，只会悄悄削弱安全性。
 */
class AuthFlowTest extends ApiTestSupport {

    @Test
    @DisplayName("登录成功返回令牌，且角色为 ADMIN")
    void loginReturnsToken() throws Exception {
        Map<String, Object> body = Map.of("username", ADMIN_USERNAME, "password", ADMIN_PASSWORD);

        var actions = postJson("/api/auth/login", null, body);

        assertStatus(actions, 200, 0);
        assertThat(jsonString(actions, "/data/token")).isNotBlank();
        assertThat(jsonString(actions, "/data/tokenType")).isEqualTo("Bearer");
        assertThat(jsonString(actions, "/data/user/role")).isEqualTo("ADMIN");
        assertThat(jsonString(actions, "/data/user/passwordHash"))
                .as("响应体绝不能带上密码哈希")
                .isNull();
    }

    @Test
    @DisplayName("用户不存在与密码错误：文案和业务码完全一致，不可枚举用户名")
    void loginFailuresAreIndistinguishable() throws Exception {
        var unknownUser = postJson("/api/auth/login", null,
                Map.of("username", "ittest_nobody_" + System.nanoTime(), "password", "whatever123"));
        var wrongPassword = postJson("/api/auth/login", null,
                Map.of("username", ADMIN_USERNAME, "password", "definitely-wrong"));

        assertStatus(unknownUser, 200, 4010);
        assertStatus(wrongPassword, 200, 4010);

        String a = jsonString(unknownUser, "/message");
        String b = jsonString(wrongPassword, "/message");
        assertThat(a)
                .as("两种失败必须返回同一句文案，否则攻击者据此就能枚举用户名")
                .isEqualTo(b);
    }

    @Test
    @DisplayName("未带令牌访问 /auth/me：HTTP 401 且业务码 4010")
    void meRequiresToken() throws Exception {
        // 两个都要验：前端 axios 按 HTTP 状态决定是否跳登录页，
        // 只给业务码会让它以为请求成功
        assertStatus(getJson("/api/auth/me", null), 401, 4010);
    }

    @Test
    @DisplayName("带令牌访问 /auth/me 能拿到自己")
    void meReturnsCurrentUser() throws Exception {
        String token = adminToken();

        var actions = getJson("/api/auth/me", token);

        assertStatus(actions, 200, 0);
        assertThat(jsonString(actions, "/data/username")).isEqualTo(ADMIN_USERNAME);
    }

    @Test
    @DisplayName("注册成功直接返回令牌（注册即登录），新用户角色是 USER")
    void registerReturnsToken() throws Exception {
        String username = newUsername();
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", TEST_PASSWORD);
        body.put("email", username + "@example.com");

        var actions = postJson("/api/auth/register", null, body);

        assertStatus(actions, 200, 0);
        assertThat(jsonString(actions, "/data/token")).isNotBlank();
        assertThat(jsonString(actions, "/data/user/role"))
                .as("新注册用户绝不能默认拿到管理员角色")
                .isEqualTo("USER");

        // 注册完应当能用这个密码登录
        assertThat(login(username, TEST_PASSWORD)).isNotBlank();
    }

    @Test
    @DisplayName("用户名重复注册返回 4009")
    void registerDuplicateUsername() throws Exception {
        String username = newUsername();
        createUser(username, TEST_PASSWORD, "USER");
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", TEST_PASSWORD);

        assertStatus(postJson("/api/auth/register", null, body), 200, 4009);
    }

    @Test
    @DisplayName("注册参数非法（密码过短 / 邮箱格式错）返回 4000")
    void registerValidation() throws Exception {
        Map<String, Object> shortPassword = new HashMap<>();
        shortPassword.put("username", newUsername());
        shortPassword.put("password", "123");
        assertStatus(postJson("/api/auth/register", null, shortPassword), 200, 4000);

        Map<String, Object> badEmail = new HashMap<>();
        badEmail.put("username", newUsername());
        badEmail.put("password", TEST_PASSWORD);
        badEmail.put("email", "not-an-email");
        assertStatus(postJson("/api/auth/register", null, badEmail), 200, 4000);
    }
}
