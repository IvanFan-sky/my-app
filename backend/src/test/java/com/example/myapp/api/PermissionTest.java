package com.example.myapp.api;

import com.example.myapp.common.UserRole;
import com.example.myapp.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 权限边界：管理接口只对 ADMIN 开放，个人中心对任何登录用户开放。
 *
 * <p><b>这一组是整个项目最该自动化守住的测试</b>。权限回归不会报错、不会抛异常，
 * 只会静静地让任何登录用户都能改别人的密码 —— 靠人眼 review 是守不住的。
 *
 * <p>这些断言原先靠手工脚本验证，现已转为 JUnit 并进 CI。
 */
class PermissionTest extends ApiTestSupport {

    @Test
    @DisplayName("管理员可以访问用户管理整组接口")
    void adminCanAccessUserManagement() throws Exception {
        String token = adminToken();

        assertStatus(getJson("/api/users?current=1&size=5", token), 200, 0);
        assertStatus(getJson("/api/users/stats", token), 200, 0);
        assertStatus(getJson("/api/users/" + adminId(), token), 200, 0);
    }

    @Test
    @DisplayName("普通用户访问用户管理：HTTP 403 + 业务码 4030")
    void normalUserForbiddenOnUserManagement() throws Exception {
        String username = newUsername();
        long id = createUser(username, TEST_PASSWORD, UserRole.USER);
        String token = login(username, TEST_PASSWORD);

        // 逐个接口验：只验列表会漏掉「列表拦了但改删没拦」这种更危险的情况
        assertStatus(getJson("/api/users?current=1&size=5", token), 403, 4030);
        assertStatus(getJson("/api/users/stats", token), 403, 4030);
        assertStatus(getJson("/api/users/" + id, token), 403, 4030);
        assertStatus(postJson("/api/users", token,
                Map.of("username", newUsername(), "password", TEST_PASSWORD)), 403, 4030);
        assertStatus(putJson("/api/users/" + id, token,
                Map.of("username", username, "password", "hacked123")), 403, 4030);
        assertStatus(deleteJson("/api/users/" + id, token), 403, 4030);
    }

    @Test
    @DisplayName("普通用户仍可访问自己的个人中心接口")
    void normalUserCanAccessProfile() throws Exception {
        String username = newUsername();
        createUser(username, TEST_PASSWORD, UserRole.USER);
        String token = login(username, TEST_PASSWORD);

        // 权限收紧不能误伤个人中心：这两条是「只要登录就能用」的
        assertStatus(getJson("/api/auth/me", token), 200, 0);
        assertStatus(putJson("/api/auth/profile", token, Map.of("nickname", "普通用户")), 200, 0);
    }

    @Test
    @DisplayName("在个人资料请求里塞 role 无法提权")
    void cannotEscalateThroughProfile() throws Exception {
        String username = newUsername();
        long id = createUser(username, TEST_PASSWORD, UserRole.USER);
        String token = login(username, TEST_PASSWORD);

        // ProfileForm 里根本没有 role 字段，Jackson 会忽略未知属性 ——
        // 这是「不可能提权」的结构保证，这条测试就是防止有人日后给它加上 role
        Map<String, Object> escalation = new HashMap<>();
        escalation.put("nickname", "想提权的人");
        escalation.put("role", UserRole.ADMIN);
        assertStatus(putJson("/api/auth/profile", token, escalation), 200, 0);

        String roleInDb = jdbcTemplate.queryForObject(
                "SELECT role FROM sys_user WHERE id = ?", String.class, id);
        assertThat(roleInDb)
                .as("请求体里的 role 必须被忽略，库里的角色不能变")
                .isEqualTo(UserRole.USER);

        // 提权失败的直接证据：管理接口仍然 403
        assertStatus(getJson("/api/users", token), 403, 4030);
    }

    @Test
    @DisplayName("管理员不能修改自己的角色（防止把唯一管理员降级后锁死系统）")
    void adminCannotChangeOwnRole() throws Exception {
        String token = adminToken();
        long id = adminId();

        Map<String, Object> body = new HashMap<>();
        body.put("username", ADMIN_USERNAME);
        body.put("role", UserRole.USER);

        assertStatus(putJson("/api/users/" + id, token, body), 200, 4000);

        String roleInDb = jdbcTemplate.queryForObject(
                "SELECT role FROM sys_user WHERE id = ?", String.class, id);
        assertThat(roleInDb).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("管理员不能删除自己")
    void adminCannotDeleteSelf() throws Exception {
        assertStatus(deleteJson("/api/users/" + adminId(), adminToken()), 200, 4000);
    }

    @Test
    @DisplayName("非法角色值被拒绝，合法角色可以设置")
    void roleValueIsWhitelisted() throws Exception {
        String token = adminToken();

        Map<String, Object> illegal = new HashMap<>();
        illegal.put("username", newUsername());
        illegal.put("password", TEST_PASSWORD);
        illegal.put("role", "HACKER");
        assertStatus(postJson("/api/users", token, illegal), 200, 4000);

        String username = newUsername();
        Map<String, Object> legal = new HashMap<>();
        legal.put("username", username);
        legal.put("password", TEST_PASSWORD);
        legal.put("role", UserRole.ADMIN);
        var created = postJson("/api/users", token, legal);
        assertStatus(created, 200, 0);
        assertThat(jsonString(created, "/data/role")).isEqualTo(UserRole.ADMIN);
    }

    private long adminId() {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ?", Long.class, ADMIN_USERNAME);
        assertThat(id)
                .as("需要 Flyway V1 的种子管理员 %s，请先启动一次让迁移执行", ADMIN_USERNAME)
                .isNotNull();
        return id;
    }
}
