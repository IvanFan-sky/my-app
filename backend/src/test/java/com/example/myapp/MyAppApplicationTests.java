package com.example.myapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 上下文冒烟测试：验证 Spring 容器能完整装配（数据源、Redis、MyBatis-Plus、MVC 都在）。
 *
 * <p>注意：这个测试需要 MySQL 与 Redis 都可用，且 my_app 库已建好、
 * 数据库密码已配置。首次跑之前请先执行 sql/init.sql。若只想跳过测试，
 * 用 mvn -DskipTests package。
 */
@SpringBootTest
class MyAppApplicationTests {

    @Test
    void contextLoads() {
    }
}
