package com.example.myapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码哈希相关的 Bean。
 *
 * <p>只引了 spring-security-crypto 这一个模块（见 pom.xml 注释），
 * 因此这里不需要、也不应该有任何 Web 安全过滤器配置 —— 鉴权走
 * {@link com.example.myapp.security.AuthInterceptor}。
 */
@Configuration
public class SecurityBeanConfig {

    /**
     * 只声明 PasswordEncoder 接口，实现类里不 new BCryptPasswordEncoder，
     * 这样将来换 Argon2 / PBKDF2 时只改这一处。
     *
     * <p>强度用 BCrypt 默认的 10（2^10 次迭代）。调高会同时增加登录延迟和抗暴力破解成本，
     * 需要按实际压测再定，不要凭感觉改。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
