package com.example.myapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 上下文冒烟测试：验证 Spring 容器能完整装配（数据源、Redis、MyBatis-Plus、MVC 都在）。
 *
 * <p>注意：这个测试需要 MySQL 与 Redis 都可用；目标库由 test profile 指定为
 * my_app_test（首次运行会自动建库，表结构交给 Flyway）。若只想跳过测试，用
 * {@code mvn -DskipTests package}。
 *
 * <p>它只验证「容器能装配」；业务与权限的行为断言在
 * {@code api/AuthFlowTest}、{@code api/PermissionTest}、{@code api/ProfileTest}。
 */
@SpringBootTest
@ActiveProfiles("test")
class MyAppApplicationTests {

    @Test
    void contextLoads() {
    }
}
