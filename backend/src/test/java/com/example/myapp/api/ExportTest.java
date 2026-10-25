package com.example.myapp.api;

import com.example.myapp.common.UserRole;
import com.example.myapp.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/**
 * 导出 CSV 的行为测试。
 *
 * <p>导出改成了流式分批（每批 {@code EXPORT_BATCH_SIZE} 行），
 * 而分批循环是最容易悄悄出错的地方：少写一行、重复一行、提前结束、
 * 死循环，任何一种都不会抛异常 —— 只会让导出的表格「看起来有数据」。
 * 所以这里必须真的跨过批边界去数行，用一行数据测是测不出来的。
 */
class ExportTest extends ApiTestSupport {

    /** 略大于一批（500），确保至少跨 2 批，且最后一批不满 */
    private static final int BULK_COUNT = 620;

    /** 发导出请求并取回正文：流式响应必须等异步处理完成才读得到 */
    private String exportCsv(String keyword, String token) throws Exception {
        MvcResult pending = mockMvc.perform(get("/api/users/export")
                        .param("keyword", keyword)
                        .header("Authorization", "Bearer " + token))
                .andExpect(request().asyncStarted())
                .andReturn();
        if (pending.getRequest().isAsyncStarted()) {
            return mockMvc.perform(asyncDispatch(pending))
                    .andReturn().getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);
        }
        return pending.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** 把 CSV 正文拆成数据行（去掉 BOM、表头与末尾空行） */
    private static List<String> dataRows(String body) {
        String[] lines = body.replace("\uFEFF", "").split("\r\n");
        return Arrays.stream(lines)
                .skip(1)                       // 表头
                .filter(line -> !line.isEmpty())
                .toList();
    }

    @Test
    @DisplayName("导出跨批（620 行 > 一批 500）时行数准确、无重复、无遗漏")
    void exportAcrossBatches() throws Exception {
        String token = adminToken();
        String prefix = TEST_PREFIX + "bulk";
        String hash = new BCryptPasswordEncoder().encode(TEST_PASSWORD);

        List<Object[]> args = new ArrayList<>(BULK_COUNT);
        for (int i = 0; i < BULK_COUNT; i++) {
            String username = prefix + String.format("%04d", i);
            args.add(new Object[]{username, hash, UserRole.USER, username + "@example.com", 30});
        }

        try {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO sys_user (username, password_hash, role, email, age) "
                            + "VALUES (?, ?, ?, ?, ?)", args);

            List<String> rows = dataRows(exportCsv(prefix, token));

            // 行数：少一行是「提前 break」，多一行是「重复读了一批」
            assertThat(rows)
                    .as("620 个用户应当导出 620 行")
                    .hasSize(BULK_COUNT);

            // 用户名唯一且齐全：只断行数的话，「重复 1 个 + 漏 1 个」也能凑对
            Set<String> usernames = new HashSet<>();
            for (String row : rows) {
                // 列顺序：ID,用户名,显示名,角色,邮箱,年龄,创建时间,更新时间
                usernames.add(row.split(",")[1]);
            }
            assertThat(usernames)
                    .as("导出的用户名不应有重复或遗漏")
                    .hasSize(BULK_COUNT);
            assertThat(usernames).contains(prefix + "0000", prefix + String.format("%04d", BULK_COUNT - 1));
        } finally {
            // 必须物理删除：逻辑删除会让 username 继续占着唯一索引
            jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE ?", prefix + "%");
        }
    }

    @Test
    @DisplayName("导出遵守 keyword 过滤，不会把全部用户都写进去")
    void exportRespectsKeyword() throws Exception {
        String token = adminToken();
        String wanted = newUsername();
        String other = newUsername();
        createUser(wanted, TEST_PASSWORD, UserRole.USER);
        createUser(other, TEST_PASSWORD, UserRole.USER);

        List<String> rows = dataRows(exportCsv(wanted, token));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).contains(wanted);
        assertThat(rows.get(0)).doesNotContain(other);
    }
}
