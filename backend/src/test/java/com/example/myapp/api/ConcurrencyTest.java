package com.example.myapp.api;

import com.example.myapp.common.ErrorCode;
import com.example.myapp.common.UserRole;
import com.example.myapp.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发场景：验证「逻辑对但时序错」时不会翻车。
 *
 * <p><b>为什么单线程测试不够</b>：这几个 bug 都只在竞争窗口里出现 ——
 * 查重与插入之间的间隙、读-改-写的间隙、两个请求同时通过存在性检查。
 * 单线程跑一百遍都是绿的，线上并发一上来就报 500 或丢更新。
 *
 * <p><b>为什么用 MockMvc 并发而不是直接调 Service</b>：这里要验的不只是
 * 「数据库唯一索引拦住了」，还有「拦住之后返回给前端的是什么码」。
 * 抢注失败返回 4009 还是 5000，是这轮修复的核心，只有走完
 * Controller → 异常 → GlobalExceptionHandler 这条链才测得到。
 *
 * <p><b>断言只写「时序无关」的结论</b>：不写「第一个线程必须赢」这种依赖调度顺序的断言，
 * 否则测试会随机红。比如抢注只断言「恰好一个成功、其余都是 4009」，
 * 谁赢不重要。
 */
class ConcurrencyTest extends ApiTestSupport {

    /** 并发度：够产生竞争，又不至于把 BCrypt 的 CPU 打满导致 CI 超时 */
    private static final int THREADS = 8;

    /**
     * 让 {@code threads} 个任务尽量同时起跑，收集各自的返回值。
     *
     * <p>用 {@link CountDownLatch} 而不是直接 submit：submit 之后线程会被立即调度，
     * 先提交的可能已经跑完了，根本形不成竞争窗口。
     */
    private <T> List<T> runConcurrently(int threads, Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch startGate = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>(threads);
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    return task.call();
                }));
            }
            startGate.countDown();

            List<T> results = new ArrayList<>(threads);
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    /** 从响应体里取业务码（不用 assertStatus，因为并发下要收集而不是断言） */
    private int codeOf(ResultActions actions) throws Exception {
        String body = actions.andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).get("code").asInt();
    }

    @Test
    @DisplayName("并发抢注同一用户名：恰好一个成功，其余全是 4009（不是 5000）")
    void concurrentRegisterSameUsername() throws Exception {
        String username = newUsername();
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", TEST_PASSWORD);
        body.put("email", username + "@example.com");

        List<Integer> codes = runConcurrently(THREADS,
                () -> codeOf(postJson("/api/auth/register", null, body)));

        assertThat(codes.stream().filter(code -> code == 0).count())
                .as("同名并发注册只能有一个成功，实际码=%s", codes)
                .isEqualTo(1);
        assertThat(codes.stream().filter(code -> code == ErrorCode.CONFLICT).count())
                .as("其余请求必须拿到 4009「用户名已被占用」，实际码=%s", codes)
                .isEqualTo(THREADS - 1);
        /*
         * 这行是这条测试的重点。
         *
         * 查重（count）与插入之间有窗口：多个线程可能同时通过查重，
         * 最终只有唯一索引能拦住。如果没有捕获 DuplicateKeyException，
         * 落后的那几个会落到兜底异常分支变成 5000 —— 用户看到「服务器内部错误」，
         * 实际只是用户名重了。
         */
        assertThat(codes)
                .as("抢注失败不能表现为服务器错误")
                .doesNotContain(ErrorCode.SERVER_ERROR);

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username = ?", Integer.class, username);
        assertThat(rows).as("库里只应留下一条记录").isEqualTo(1);
    }

    @Test
    @DisplayName("并发重置密码：token_version 精确自增 N 次，不丢更新")
    void concurrentPasswordResetDoesNotLoseIncrements() throws Exception {
        String username = newUsername();
        long id = createUser(username, TEST_PASSWORD, UserRole.USER);
        String token = adminToken();

        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", "concurrent123");

        List<Integer> codes = runConcurrently(THREADS,
                () -> codeOf(putJson("/api/users/" + id, token, body)));

        assertThat(codes).as("并发重置密码都该成功，实际码=%s", codes).containsOnly(0);

        Integer version = jdbcTemplate.queryForObject(
                "SELECT token_version FROM sys_user WHERE id = ?", Integer.class, id);

        /*
         * 为什么这条能抓到真问题：如果版本号是「读出来 +1 写回去」，
         * 并发下就会丢更新（8 个请求可能只 +2），而表现只是「某些旧令牌
         * 侥幸还能用」—— 不会报错、不会告警。用 SQL 的 `token_version + 1`
         * 才能保证每次请求都真的加了一次。
         */
        assertThat(version)
                .as("%d 次并发重置应当让版本号精确增加 %d，实际=%s", THREADS, THREADS, version)
                .isEqualTo(THREADS);
    }

    @Test
    @DisplayName("并发删除同一用户：只有 0 或 4004，不出现 500")
    void concurrentDeleteSameUser() throws Exception {
        String username = newUsername();
        long id = createUser(username, TEST_PASSWORD, UserRole.USER);
        String token = adminToken();

        List<Integer> codes = runConcurrently(THREADS,
                () -> codeOf(deleteJson("/api/users/" + id, token)));

        /*
         * 不断言「恰好一个 0」：两个请求可能同时通过存在性检查，都走完 removeById
         * 并都返回成功 —— 这是逻辑删除的固有语义（第二次 UPDATE 影响 0 行）。
         * 要守的是「要么删成功，要么明确说已不存在」，而不是 500。
         */
        assertThat(codes.stream().allMatch(code -> code == 0 || code == ErrorCode.NOT_FOUND))
                .as("并发删除只允许成功或 4004，实际码=%s", codes)
                .isTrue();
        assertThat(codes).contains(0);

        // 最终状态：查不到了
        assertStatus(getJson("/api/users/" + id, token), 200, ErrorCode.NOT_FOUND);
    }
}
