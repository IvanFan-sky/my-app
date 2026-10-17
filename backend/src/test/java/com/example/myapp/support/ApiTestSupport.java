package com.example.myapp.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 接口集成测试的基类：提供发请求、断言响应、造/清测试数据的公共方法。
 *
 * <p><b>为什么是集成测试而不是单元测试</b>：这一轮要守住的东西 ——
 * 权限边界、缓存失效、空值覆盖、密码校验 —— 全都发生在
 * Spring MVC + MyBatis-Plus + Redis 的**协作**上。用 Mockito 把 Mapper 和 Redis
 * 都换成假的，测试会全绿，但真实环境里的越权、脏缓存照样会发生。
 *
 * <p><b>为什么需要真实 MySQL / Redis</b>：同上。所以 {@code mvn test} 依赖本机
 * 基础设施（见 AGENTS.md）。CI 里由 service 容器提供。
 *
 * <p><b>测试数据的约定</b>：
 * <ul>
 *   <li>所有造出来的用户名以 {@value #TEST_PREFIX} 开头，清理时按前缀硬删除，
 *       不会碰到种子账号。</li>
 *   <li>用**物理删除**清理（{@code DELETE} 而不是 {@code removeById}）：
 *       逻辑删除会让 username 继续占着唯一索引，跑第二次就会撞 Duplicate。</li>
 *   <li>同时删掉对应的 Redis 缓存 key，否则下一次测试可能读到上一个测试的残留。</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class ApiTestSupport {

    /** 测试数据用户名统一前缀，清理时按它匹配 */
    protected static final String TEST_PREFIX = "ittest";

    /** 所有测试用户共用的初始密码 */
    protected static final String TEST_PASSWORD = "ittest123";

    /** 种子管理员。依赖 Flyway V1 的种子数据 */
    protected static final String ADMIN_USERNAME = "alice";
    protected static final String ADMIN_PASSWORD = "alice123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    /** 本轮创建出来的用户名，@AfterEach 统一清理 */
    private final List<String> createdUsernames = new java.util.ArrayList<>();

    // ------------------------------------------------------------------
    // 请求
    // ------------------------------------------------------------------

    protected ResultActions getJson(String url, String token) throws Exception {
        var builder = get(url);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(builder);
    }

    protected ResultActions postJson(String url, String token, Object body) throws Exception {
        var builder = post(url).contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(builder);
    }

    protected ResultActions putJson(String url, String token, Object body) throws Exception {
        var builder = put(url).contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(builder);
    }

    protected ResultActions deleteJson(String url, String token) throws Exception {
        var builder = delete(url);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(builder);
    }

    // ------------------------------------------------------------------
    // 断言
    // ------------------------------------------------------------------

    /**
     * 断言 HTTP 状态与 body 里的业务码。
     *
     * <p>两者都要验：本项目约定「鉴权失败时 HTTP 401 与 code 4010 同时给出」，
     * 只看业务码会漏掉「HTTP 给错、前端按状态码判断就会走错分支」这类问题。
     */
    protected void assertStatus(ResultActions actions, int expectedHttp, int expectedCode) throws Exception {
        var response = actions.andReturn().getResponse();
        // 显式按 UTF-8 解码：默认编码在 Windows 上是 GBK，
        // 中文文案比较会变成「看起来一样但断言失败」的怪事
        String body = response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(response.getStatus())
                .as("HTTP 状态码，响应体=%s", body)
                .isEqualTo(expectedHttp);
        assertThat(readCode(body))
                .as("业务码，响应体=%s", body)
                .isEqualTo(expectedCode);
    }

    /** 断言成功（HTTP 200 + code 0），并把 data 解析成给定类型 */
    protected <T> T assertOkData(ResultActions actions, Class<T> type) throws Exception {
        assertStatus(actions, 200, 0);
        String body = actions.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return objectMapper.readTree(body).get("data").traverse(objectMapper).readValueAs(type);
    }

    /** 从响应体里取字段（JSON Pointer 风格，如 /data/token） */
    protected String jsonString(ResultActions actions, String pointer) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        var node = objectMapper.readTree(body).at(pointer);
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private int readCode(String body) throws Exception {
        var node = objectMapper.readTree(body).get("code");
        assertThat(node).as("响应体里没有 code 字段：%s", body).isNotNull();
        return node.asInt();
    }

    // ------------------------------------------------------------------
    // 测试数据
    // ------------------------------------------------------------------

    /** 生成一个唯一的测试用户名，并登记以便清理 */
    protected String newUsername() {
        String username = TEST_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        createdUsernames.add(username);
        return username;
    }

    /**
     * 直接写库造一个用户，返回其 id。
     *
     * <p>为什么不走 POST /api/users：那个接口要求管理员令牌，而我们恰恰需要
     * 在「没有管理员令牌」的前提下造普通用户。直接写库最直接。
     */
    protected long createUser(String username, String rawPassword, String role) {
        String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .encode(rawPassword);
        jdbcTemplate.update(
                "INSERT INTO sys_user (username, password_hash, role, email, age) VALUES (?, ?, ?, ?, ?)",
                username, hash, role, username + "@example.com", 30);
        createdUsernames.add(username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ?", Long.class, username);
        assertThat(id).as("刚插入的用户应当查得到：%s", username).isNotNull();
        return id;
    }

    /** 登录并取令牌，顺带断言登录成功 */
    protected String login(String username, String password) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        ResultActions actions = postJson("/api/auth/login", null, body);
        assertStatus(actions, 200, 0);
        String token = jsonString(actions, "/data/token");
        assertThat(token).as("登录成功应返回令牌").isNotBlank();
        return token;
    }

    protected String adminToken() throws Exception {
        return login(ADMIN_USERNAME, ADMIN_PASSWORD);
    }

    // ------------------------------------------------------------------
    // 清理
    // ------------------------------------------------------------------

    /**
     * 清掉本轮造的数据（物理删除 + 删缓存）。
     *
     * <p>物理删除是刻意的：逻辑删除会让 username 继续占住唯一索引，
     * 同一个测试跑第二次就会撞 Duplicate entry。
     */
    @AfterEach
    void cleanUpTestData() {
        if (createdUsernames.isEmpty()) {
            return;
        }
        for (String username : createdUsernames) {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT id FROM sys_user WHERE username = ?", Long.class, username);
            for (Long id : ids) {
                redisTemplate.delete("my-app:user:" + id);
            }
            jdbcTemplate.update("DELETE FROM sys_user WHERE username = ?", username);
            // 登录失败计数也要清：否则同名的下一个测试一上来就是「已锁定」状态
            redisTemplate.delete("my-app:login:fail:" + username);
            // 审计日志也要清：它按设计「只增不改」，不主动删的话每跑一次测试
            // 就多一批 ittest* 记录，越积越多，还会干扰按关键字查日志的断言
            jdbcTemplate.update(
                    "DELETE FROM sys_audit_log WHERE actor_username = ? OR target_username = ?",
                    username, username);
            // 批量删除这类动作用户名在 detail 里（target 无法归到单个对象），
            // 按 detail 再扫一遍，否则测试每天都会在 alice 的审计里留下垃圾记录
            jdbcTemplate.update("DELETE FROM sys_audit_log WHERE detail LIKE ?", "%ittest%");
        }
        createdUsernames.clear();
    }
}
