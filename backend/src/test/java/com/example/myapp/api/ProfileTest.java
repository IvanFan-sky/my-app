package com.example.myapp.api;

import com.example.myapp.common.UserRole;
import com.example.myapp.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 个人中心：改资料、改密码、以及缓存失效。
 *
 * <p>这里有三条**曾经真实出过问题**的回归点，值得单独说：
 * <ol>
 *   <li><b>空值覆盖</b>：MyBatis-Plus 默认 NOT_NULL 更新策略会跳过 null 字段，
 *       于是「清空昵称」变成「不修改昵称」。{@link #updateProfileClearsOmittedFields} 守这条。</li>
 *   <li><b>原密码错误不能返回 4010</b>：前端见到 4010 会登出，
 *       用户只是打错一次密码就被踢出去。{@link #changePasswordWithWrongOldPassword} 守这条。</li>
 *   <li><b>改完资料要失效缓存</b>：缓存里存的是 UserVO，不失效会让
 *       「改完刷新还是旧值」。{@link #profileUpdateEvictsCache} 守这条。</li>
 * </ol>
 */
class ProfileTest extends ApiTestSupport {

    @Test
    @DisplayName("改资料后能读回新值")
    void updateProfilePersists() throws Exception {
        String username = newUsername();
        long id = createUser(username, TEST_PASSWORD, UserRole.USER);
        String token = login(username, TEST_PASSWORD);

        Map<String, Object> body = new HashMap<>();
        body.put("nickname", "测试昵称");
        body.put("email", "changed@example.com");
        body.put("bio", "我的简介");
        body.put("age", 33);

        var actions = putJson("/api/auth/profile", token, body);
        assertStatus(actions, 200, 0);
        assertThat(jsonString(actions, "/data/nickname")).isEqualTo("测试昵称");

        // 换一条路径读回来：/auth/me 会重新查库，能确认真的落库了
        var me = getJson("/api/auth/me", token);
        assertThat(jsonString(me, "/data/nickname")).isEqualTo("测试昵称");
        assertThat(jsonString(me, "/data/bio")).isEqualTo("我的简介");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT age FROM sys_user WHERE id = ?", Integer.class, id)).isEqualTo(33);
    }

    @Test
    @DisplayName("不传的字段会被清空（NOT_NULL 更新策略的回归点）")
    void updateProfileClearsOmittedFields() throws Exception {
        String username = newUsername();
        long id = createUser(username, TEST_PASSWORD, UserRole.USER);
        String token = login(username, TEST_PASSWORD);

        Map<String, Object> withValues = new HashMap<>();
        withValues.put("nickname", "先设一个昵称");
        withValues.put("bio", "先设一个简介");
        assertStatus(putJson("/api/auth/profile", token, withValues), 200, 0);

        // 第二次请求完全不提 nickname / bio，语义应当是「清空」
        var cleared = putJson("/api/auth/profile", token, Map.of("age", 20));
        assertStatus(cleared, 200, 0);

        assertThat(jsonString(cleared, "/data/nickname"))
                .as("不传即为 null，不能被旧值悄悄保留")
                .isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT nickname FROM sys_user WHERE id = ?", String.class, id)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT bio FROM sys_user WHERE id = ?", String.class, id)).isNull();
    }

    @Test
    @DisplayName("原密码错误返回 4000（不是 4010），且令牌仍然有效")
    void changePasswordWithWrongOldPassword() throws Exception {
        String username = newUsername();
        createUser(username, TEST_PASSWORD, UserRole.USER);
        String token = login(username, TEST_PASSWORD);

        Map<String, Object> body = Map.of("oldPassword", "wrong-one", "newPassword", "newPass123");
        assertStatus(putJson("/api/auth/password", token, body), 200, 4000);

        // 关键：不能因为这个错误把用户登出
        assertStatus(getJson("/api/auth/me", token), 200, 0);
    }

    @Test
    @DisplayName("新密码过短 / 与原密码相同都返回 4000")
    void changePasswordValidations() throws Exception {
        String username = newUsername();
        createUser(username, TEST_PASSWORD, UserRole.USER);
        String token = login(username, TEST_PASSWORD);

        assertStatus(putJson("/api/auth/password", token,
                Map.of("oldPassword", TEST_PASSWORD, "newPassword", "123")), 200, 4000);
        assertStatus(putJson("/api/auth/password", token,
                Map.of("oldPassword", TEST_PASSWORD, "newPassword", TEST_PASSWORD)), 200, 4000);
    }

    @Test
    @DisplayName("改密码成功后：新密码可登录，旧密码失效")
    void changePasswordTakesEffect() throws Exception {
        String username = newUsername();
        createUser(username, TEST_PASSWORD, UserRole.USER);
        String token = login(username, TEST_PASSWORD);
        String newPassword = "brandNew456";

        assertStatus(putJson("/api/auth/password", token,
                Map.of("oldPassword", TEST_PASSWORD, "newPassword", newPassword)), 200, 0);

        assertStatus(postJson("/api/auth/login", null,
                Map.of("username", username, "password", TEST_PASSWORD)), 200, 4010);
        assertThat(login(username, newPassword)).isNotBlank();
    }

    @Test
    @DisplayName("改资料后缓存被失效，管理员读到的不是旧值")
    void profileUpdateEvictsCache() throws Exception {
        String username = newUsername();
        long id = createUser(username, TEST_PASSWORD, UserRole.USER);
        String token = login(username, TEST_PASSWORD);
        String adminToken = adminToken();

        // 先让 GET /users/{id} 把 UserVO 写进缓存
        assertStatus(getJson("/api/users/" + id, adminToken), 200, 0);

        assertStatus(putJson("/api/auth/profile", token, Map.of("nickname", "改过了")), 200, 0);

        // 读回来必须是新值。如果缓存没失效，这里会拿到旧昵称（null）
        var after = getJson("/api/users/" + id, adminToken);
        assertStatus(after, 200, 0);
        assertThat(jsonString(after, "/data/nickname"))
                .as("写路径漏掉 userCache.evict 时，这里会读到旧值")
                .isEqualTo("改过了");
    }
}
