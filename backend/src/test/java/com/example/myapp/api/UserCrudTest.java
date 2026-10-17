package com.example.myapp.api;

import com.example.myapp.common.UserRole;
import com.example.myapp.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用户管理 CRUD、分页搜索、缓存计数。
 *
 * <p>这一组覆盖 CRUD 与缓存这类「功能性」行为：失败方式是「功能坏了」
 * 而不是「权限漏了」，所以风险等级低于 {@link PermissionTest}，
 * 但同样不能没有 —— 换个人改分页或搜索条件，最容易悄悄改坏这里。
 */
class UserCrudTest extends ApiTestSupport {

    @Test
    @DisplayName("新增用户：缺密码被拒，正常新增返回带时间戳的用户")
    void createUser() throws Exception {
        String token = adminToken();

        // 新增必须给密码，否则建出来的账号无法登录
        Map<String, Object> noPassword = new HashMap<>();
        noPassword.put("username", newUsername());
        assertStatus(postJson("/api/users", token, noPassword), 200, 4000);

        String username = newUsername();
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", TEST_PASSWORD);
        body.put("nickname", "新建的人");
        body.put("email", username + "@example.com");
        body.put("age", 26);

        var created = postJson("/api/users", token, body);
        assertStatus(created, 200, 0);
        assertThat(jsonString(created, "/data/username")).isEqualTo(username);
        assertThat(jsonString(created, "/data/role"))
                .as("未指定角色时应当落到最小权限 USER")
                .isEqualTo(UserRole.USER);
        // createdAt 由数据库生成，写完后必须回读才能拿到（曾经返回 null 过）
        assertThat(jsonString(created, "/data/createdAt")).isNotBlank();
    }

    @Test
    @DisplayName("分页与关键字搜索：用户名、邮箱都能命中")
    void pagingAndKeywordSearch() throws Exception {
        String token = adminToken();
        String username = newUsername();
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", TEST_PASSWORD);
        // 邮箱里放一个独特片段，验证「keyword 也匹配邮箱」
        body.put("email", "kw" + username.substring(5) + "@example.com");
        assertStatus(postJson("/api/users", token, body), 200, 0);

        var byName = getJson("/api/users?keyword=" + username + "&current=1&size=10", token);
        assertStatus(byName, 200, 0);
        assertThat(jsonString(byName, "/data/total")).isEqualTo("1");

        String emailFragment = "kw" + username.substring(5);
        var byEmail = getJson("/api/users?keyword=" + emailFragment + "&current=1&size=10", token);
        assertStatus(byEmail, 200, 0);
        assertThat(jsonString(byEmail, "/data/total"))
                .as("keyword 应当同时匹配邮箱，否则用户只能按用户名找人")
                .isEqualTo("1");

        var noMatch = getJson("/api/users?keyword=zzz_no_such_user_zzz&current=1&size=10", token);
        assertStatus(noMatch, 200, 0);
        assertThat(jsonString(noMatch, "/data/total")).isEqualTo("0");
    }

    @Test
    @DisplayName("修改用户：password 留空表示不改密码")
    void updateKeepsPasswordWhenBlank() throws Exception {
        String username = newUsername();
        long id = createUser(username, TEST_PASSWORD, UserRole.USER);
        String token = adminToken();

        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("nickname", "改过的昵称");
        // 完全不传 password：语义是「不改密码」，不是「把密码清空」
        var updated = putJson("/api/users/" + id, token, body);
        assertStatus(updated, 200, 0);
        assertThat(jsonString(updated, "/data/nickname")).isEqualTo("改过的昵称");

        // 密码没被清掉：原密码仍能登录
        assertThat(login(username, TEST_PASSWORD)).isNotBlank();
    }

    @Test
    @DisplayName("逻辑删除：删掉后查不到，但记录仍在库里（deleted=1）")
    void logicalDelete() throws Exception {
        String username = newUsername();
        long id = createUser(username, TEST_PASSWORD, UserRole.USER);
        String token = adminToken();

        assertStatus(deleteJson("/api/users/" + id, token), 200, 0);

        // 接口层面查不到
        assertStatus(getJson("/api/users/" + id, token), 200, 4004);

        // 但库里还在，只是 deleted = 1 —— 这是「逻辑删除」的定义，
        // 如果哪天有人改成物理删除，这条断言会提醒他确认一下
        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM sys_user WHERE username = ?", Integer.class, username);
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    @DisplayName("统计接口返回结构完整")
    void stats() throws Exception {
        var actions = getJson("/api/users/stats", adminToken());
        assertStatus(actions, 200, 0);
        assertThat(jsonString(actions, "/data/totalUsers")).isNotBlank();
        assertThat(jsonString(actions, "/data/cacheHitCount")).isNotBlank();
        assertThat(jsonString(actions, "/data/cacheMissCount")).isNotBlank();
        assertThat(jsonString(actions, "/data/cacheHitRate")).isNotBlank();
    }

    @Test
    @DisplayName("重复读取同一用户会命中 Redis 缓存")
    void secondReadHitsCache() throws Exception {
        String token = adminToken();
        long id = createUser(newUsername(), TEST_PASSWORD, UserRole.USER);

        // 先读一次把缓存写进去（可能顺带产生一次 miss）
        assertStatus(getJson("/api/users/" + id, token), 200, 0);

        Long hitsBefore = readHitCount(token);
        // 再读两次：都应当命中缓存
        assertStatus(getJson("/api/users/" + id, token), 200, 0);
        assertStatus(getJson("/api/users/" + id, token), 200, 0);
        Long hitsAfter = readHitCount(token);

        assertThat(hitsAfter - hitsBefore)
                .as("连续读同一用户应当命中缓存；若为 0，多半是 RedisConfig 的 "
                        + "activateDefaultTyping 被删了 —— 那种故障 key 和 TTL 都正常，"
                        + "只是反序列化成 LinkedHashMap 后永远命中不了")
                .isGreaterThanOrEqualTo(2);
    }

    private Long readHitCount(String token) throws Exception {
        var actions = getJson("/api/users/stats", token);
        assertStatus(actions, 200, 0);
        return Long.parseLong(jsonString(actions, "/data/cacheHitCount"));
    }
}
