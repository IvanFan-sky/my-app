package com.example.myapp.api;

import com.example.myapp.common.ErrorCode;
import com.example.myapp.common.UserRole;
import com.example.myapp.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 双令牌与令牌失效（token_version）。
 *
 * <p>这一组守的是「撤销必须真的生效」：改密码后旧 access 还能用、refresh 还能换新，
 * 那「改密码能把别的设备踢下线」就是一句空话。这类缺陷不会报错，
 * 只会让被盗的令牌一直有效，属于最该被自动化盯住的一类。
 */
class TokenLifecycleTest extends ApiTestSupport {

    /** 登录并返回 [access, refresh] */
    private String[] loginWithRefresh(String username, String password) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        var actions = postJson("/api/auth/login", null, body);
        assertStatus(actions, 200, 0);
        String access = jsonString(actions, "/data/token");
        String refresh = jsonString(actions, "/data/refreshToken");
        assertThat(access).isNotBlank();
        assertThat(refresh).isNotBlank();
        return new String[] { access, refresh };
    }

    @Test
    @DisplayName("登录返回 access 与 refresh 两个令牌")
    void loginReturnsBothTokens() throws Exception {
        String[] tokens = loginWithRefresh(ADMIN_USERNAME, ADMIN_PASSWORD);
        assertThat(tokens[0]).isNotEqualTo(tokens[1]);
    }

    @Test
    @DisplayName("refresh 能换到可用的新 access")
    void refreshIssuesUsableAccessToken() throws Exception {
        String username = newUsername();
        createUser(username, TEST_PASSWORD, UserRole.USER);
        String[] tokens = loginWithRefresh(username, TEST_PASSWORD);

        var refreshed = postJson("/api/auth/refresh", null, Map.of("refreshToken", tokens[1]));
        assertStatus(refreshed, 200, 0);
        String newAccess = jsonString(refreshed, "/data/token");
        assertThat(newAccess).isNotBlank().isNotEqualTo(tokens[0]);
        // 轮换：refresh 也换了新的
        assertThat(jsonString(refreshed, "/data/refreshToken")).isNotBlank().isNotEqualTo(tokens[1]);

        // 新 access 真能用
        assertStatus(getJson("/api/auth/me", newAccess), 200, 0);
    }

    @Test
    @DisplayName("用 access token 去刷新会被拒绝（防止绕过短有效期）")
    void cannotRefreshWithAccessToken() throws Exception {
        String username = newUsername();
        createUser(username, TEST_PASSWORD, UserRole.USER);
        String[] tokens = loginWithRefresh(username, TEST_PASSWORD);

        // 如果这里放行，access 的 15 分钟有效期就形同虚设 ——
        // 攻击者拿到 access 就能无限续期
        assertStatus(postJson("/api/auth/refresh", null, Map.of("refreshToken", tokens[0])),
                200, ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("refresh token 不能当 access 用（访问业务接口被拒）")
    void cannotUseRefreshTokenAsAccess() throws Exception {
        String username = newUsername();
        createUser(username, TEST_PASSWORD, UserRole.USER);
        String[] tokens = loginWithRefresh(username, TEST_PASSWORD);

        // refresh 有效期 7 天，若能当 access 用，短有效期就白设了
        assertStatus(getJson("/api/auth/me", tokens[1]), 401, ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("改密码后：旧 access 失效、旧 refresh 失效，但返回的新令牌可用")
    void passwordChangeRevokesOldTokens() throws Exception {
        String username = newUsername();
        createUser(username, TEST_PASSWORD, UserRole.USER);
        String[] tokens = loginWithRefresh(username, TEST_PASSWORD);
        String newPassword = "anotherPass123";

        var changed = putJson("/api/auth/password", tokens[0],
                Map.of("oldPassword", TEST_PASSWORD, "newPassword", newPassword));
        assertStatus(changed, 200, 0);

        // 1) 旧 access 立刻不能用（token_version 已 +1）
        assertStatus(getJson("/api/auth/me", tokens[0]), 401, ErrorCode.UNAUTHORIZED);

        // 2) 旧 refresh 也不行 —— 否则「踢下线」不彻底
        assertStatus(postJson("/api/auth/refresh", null, Map.of("refreshToken", tokens[1])),
                200, ErrorCode.UNAUTHORIZED);

        // 3) 但改密接口返回的新令牌必须能用，否则用户改完密码就被踢出去了
        String freshAccess = jsonString(changed, "/data/token");
        assertStatus(getJson("/api/auth/me", freshAccess), 200, 0);
    }

    @Test
    @DisplayName("管理员改别人的角色后，那个人的旧令牌立即失效")
    void roleChangeRevokesTokens() throws Exception {
        String username = newUsername();
        long id = createUser(username, TEST_PASSWORD, UserRole.USER);
        String[] tokens = loginWithRefresh(username, TEST_PASSWORD);
        String adminToken = adminToken();

        // 旧 access 先确认可用
        assertStatus(getJson("/api/auth/me", tokens[0]), 200, 0);

        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("role", UserRole.ADMIN);
        assertStatus(putJson("/api/users/" + id, adminToken, body), 200, 0);

        // 权限变了，旧令牌里那份「我是 USER」的断言已过时，必须强制重登
        assertStatus(getJson("/api/auth/me", tokens[0]), 401, ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("关键字搜索改为前缀匹配：中间片段不再命中")
    void keywordSearchIsPrefixOnly() throws Exception {
        String token = adminToken();
        String username = newUsername();
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", TEST_PASSWORD);
        body.put("email", username + "@example.com");
        assertStatus(postJson("/api/users", token, body), 200, 0);

        // 前缀命中（索引可用）
        var byPrefix = getJson("/api/users?keyword=" + username + "&current=1&size=10", token);
        assertThat(jsonString(byPrefix, "/data/total")).isEqualTo("1");

        // 中间片段不命中 —— 这是「用性能换模糊度」的取舍，明写在迁移脚本与文档里。
        // 若哪天有人把 likeRight 改回 like，这条会失败，提醒他确认是否真的要全表扫描。
        String middle = username.substring(6);
        var byMiddle = getJson("/api/users?keyword=" + middle + "&current=1&size=10", token);
        assertThat(jsonString(byMiddle, "/data/total"))
                .as("改成前缀匹配后，中间片段不应命中")
                .isEqualTo("0");
    }
}
