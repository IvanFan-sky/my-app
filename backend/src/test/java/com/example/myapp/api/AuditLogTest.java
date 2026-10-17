package com.example.myapp.api;

import com.example.myapp.common.ErrorCode;
import com.example.myapp.common.UserRole;
import com.example.myapp.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 审计日志：写入点是否真的记了、谁能查、批量删除与导出的行为。
 *
 * <p>审计的价值全在「出事时查得到」。如果某个写操作漏记了，日常毫无异常，
 * 等真出问题才发现那段历史是空白 —— 所以每个写入点都要有断言。
 */
class AuditLogTest extends ApiTestSupport {

    /** 按对象用户名查日志，返回记录列表 */
    private List<Map<String, Object>> findLogs(String keyword) throws Exception {
        var actions = getJson("/api/audit-logs?keyword=" + keyword + "&current=1&size=50",
                adminToken());
        assertStatus(actions, 200, 0);
        String body = actions.andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        var node = objectMapper.readTree(body).get("data").get("records");
        return objectMapper.convertValue(node, new com.fasterxml.jackson.core.type
                .TypeReference<List<Map<String, Object>>>() {
        });
    }

    /** 按动作查（用于批量删除这类 target 为空、信息在 detail 里的动作） */
    private List<Map<String, Object>> findLogsByAction(String action) throws Exception {
        var actions = getJson("/api/audit-logs?action=" + action + "&current=1&size=50",
                adminToken());
        assertStatus(actions, 200, 0);
        String body = actions.andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        var node = objectMapper.readTree(body).get("data").get("records");
        return objectMapper.convertValue(node, new com.fasterxml.jackson.core.type
                .TypeReference<List<Map<String, Object>>>() {
        });
    }

    private static boolean hasAction(List<Map<String, Object>> logs, String action) {
        return logs.stream().anyMatch(log -> action.equals(log.get("action")));
    }

    @Test
    @DisplayName("登录成功与失败都会留下记录，失败标记为 success=false")
    void loginIsAudited() throws Exception {
        String username = newUsername();
        createUser(username, TEST_PASSWORD, UserRole.USER);

        // 失败一次
        assertStatus(postJson("/api/auth/login", null,
                Map.of("username", username, "password", "wrong-pass")), 200, 4010);
        // 成功一次
        login(username, TEST_PASSWORD);

        List<Map<String, Object>> logs = findLogs(username);
        assertThat(hasAction(logs, "LOGIN")).as("应有登录成功记录").isTrue();
        assertThat(hasAction(logs, "LOGIN_FAIL")).as("应有登录失败记录").isTrue();
        assertThat(logs.stream()
                .filter(l -> "LOGIN_FAIL".equals(l.get("action")))
                .allMatch(l -> Boolean.FALSE.equals(l.get("success"))))
                .as("失败记录必须标 success=false")
                .isTrue();
    }

    @Test
    @DisplayName("新增 / 修改 / 删除用户都会留下记录")
    void userWritesAreAudited() throws Exception {
        String token = adminToken();
        String username = newUsername();

        // 新增
        Map<String, Object> create = new HashMap<>();
        create.put("username", username);
        create.put("password", TEST_PASSWORD);
        create.put("role", UserRole.USER);
        var created = postJson("/api/users", token, create);
        assertStatus(created, 200, 0);
        String idText = jsonString(created, "/data/id");

        // 修改角色
        Map<String, Object> update = new HashMap<>();
        update.put("username", username);
        update.put("role", UserRole.ADMIN);
        assertStatus(putJson("/api/users/" + idText, token, update), 200, 0);

        // 删除
        assertStatus(deleteJson("/api/users/" + idText, token), 200, 0);

        List<Map<String, Object>> logs = findLogs(username);
        assertThat(hasAction(logs, "CREATE_USER")).isTrue();
        assertThat(hasAction(logs, "UPDATE_USER")).isTrue();
        assertThat(hasAction(logs, "DELETE_USER")).isTrue();

        // 改角色的 detail 里应记下旧→新，这是查越权的关键线索
        String updateDetail = logs.stream()
                .filter(l -> "UPDATE_USER".equals(l.get("action")))
                .map(l -> String.valueOf(l.get("detail")))
                .findFirst().orElse("");
        assertThat(updateDetail).contains("USER -> ADMIN");
    }

    @Test
    @DisplayName("改密码只记「做过」，绝不记密码值")
    void passwordChangeDoesNotLeakPassword() throws Exception {
        String username = newUsername();
        createUser(username, TEST_PASSWORD, UserRole.USER);
        String token = login(username, TEST_PASSWORD);
        String newPassword = "leakCheck123";

        assertStatus(putJson("/api/auth/password", token,
                Map.of("oldPassword", TEST_PASSWORD, "newPassword", newPassword)), 200, 0);

        List<Map<String, Object>> logs = findLogs(username);
        assertThat(hasAction(logs, "CHANGE_PASSWORD")).isTrue();
        String all = logs.toString();
        assertThat(all)
                .as("审计日志里绝不能出现密码明文")
                .doesNotContain(newPassword)
                .doesNotContain(TEST_PASSWORD);
    }

    @Test
    @DisplayName("普通用户查不到审计日志（403/4030）")
    void normalUserCannotReadAuditLogs() throws Exception {
        String username = newUsername();
        createUser(username, TEST_PASSWORD, UserRole.USER);
        String token = login(username, TEST_PASSWORD);

        assertStatus(getJson("/api/audit-logs", token), 403, ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("批量删除：返回实际删除数，且不能包含自己")
    void batchDeleteUsers() throws Exception {
        String token = adminToken();
        String a = newUsername();
        String b = newUsername();
        long idA = createUser(a, TEST_PASSWORD, UserRole.USER);
        long idB = createUser(b, TEST_PASSWORD, UserRole.USER);

        var result = postJson("/api/users/batch-delete", token, Map.of("ids", List.of(idA, idB)));
        assertStatus(result, 200, 0);
        assertThat(jsonString(result, "/data")).isEqualTo("2");

        // 逻辑删除后查不到
        assertStatus(getJson("/api/users/" + idA, token), 200, ErrorCode.NOT_FOUND);
        assertStatus(getJson("/api/users/" + idB, token), 200, ErrorCode.NOT_FOUND);

        // 批量删除的 target 无法归到单个用户（一次删多个），
        // 信息在 detail 的 usernames 里 —— 所以按动作查，再在 detail 里找目标
        assertThat(findLogsByAction("BATCH_DELETE").stream()
                .anyMatch(log -> String.valueOf(log.get("detail")).contains(a)))
                .as("批量删除的审计 detail 里应包含被删用户名")
                .isTrue();
    }

    @Test
    @DisplayName("批量删除包含自己时被拒（4000），且不误删其他选中的人")
    void batchDeleteRejectsSelf() throws Exception {
        String token = adminToken();
        String victim = newUsername();
        long victimId = createUser(victim, TEST_PASSWORD, UserRole.USER);
        long selfId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ?", Long.class, ADMIN_USERNAME);

        assertStatus(postJson("/api/users/batch-delete", token,
                Map.of("ids", List.of(victimId, selfId))), 200, ErrorCode.PARAM_INVALID);

        // 整体拒绝：victim 不该被删掉（要么全成功要么全不动，不留半截状态）
        assertStatus(getJson("/api/users/" + victimId, token), 200, 0);
    }

    @Test
    @DisplayName("导出 CSV：Content-Type 正确、带 UTF-8 BOM、含表头")
    void exportCsv() throws Exception {
        String token = adminToken();
        String username = newUsername();
        createUser(username, TEST_PASSWORD, UserRole.USER);

        var response = mockMvc.perform(get("/api/users/export")
                        .param("keyword", username)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).startsWith("text/csv");
        assertThat(response.getHeader("Content-Disposition")).contains("attachment").contains(".csv");

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        // BOM：Excel 靠它识别 UTF-8，缺了中文会乱码
        assertThat(body).startsWith("\uFEFF");
        assertThat(body).contains("用户名").contains(username);
    }
}
