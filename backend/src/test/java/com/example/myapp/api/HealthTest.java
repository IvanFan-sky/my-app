package com.example.myapp.api;

import com.example.myapp.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 健康检查。
 *
 * <p>这个接口的价值全在「下游挂了能不能提前发现」。只探 Redis 时，
 * 数据库挂掉它仍会返回正常，工作台显示一切正常而所有业务接口都在报错 ——
 * 所以两个下游都要有断言。
 */
class HealthTest extends ApiTestSupport {

    @Test
    @DisplayName("健康检查同时探测 MySQL 与 Redis，并给出汇总位")
    void healthProbesBothDatastores() throws Exception {
        // 白名单接口，不带令牌
        var actions = getJson("/api/health", null);
        assertStatus(actions, 200, 0);

        assertThat(jsonString(actions, "/data/redis")).isEqualTo("pong");
        assertThat(jsonString(actions, "/data/mysql"))
                .as("只探 Redis 的话，MySQL 挂掉时这里仍会显示正常")
                .isEqualTo("pong");
        assertThat(jsonString(actions, "/data/healthy")).isEqualTo("true");
    }
}
