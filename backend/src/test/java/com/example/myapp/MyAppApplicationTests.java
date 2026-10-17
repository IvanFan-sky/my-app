package com.example.myapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 上下文冒烟测试：验证 Spring 容器能完整装配（数据源、Redis、MyBatis-Plus、MVC 都在）。
 *
 * <p>注意：这个测试需要 MySQL 与 Redis 都可用，且 my_app 库已存在
 * （库里的表由 Flyway 启动时自动建）。若只想跳过测试，用
 * {@code mvn -DskipTests package}。
 *
 * <p>它只验证「容器能装配」；业务与权限的行为断言在
 * {@code api/AuthFlowTest}、{@code api/PermissionTest}、{@code api/ProfileTest}。
 */
@SpringBootTest
class MyAppApplicationTests {

    @Test
    void contextLoads() {
    }
}
