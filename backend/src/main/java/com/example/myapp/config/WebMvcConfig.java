package com.example.myapp.config;

import com.example.myapp.security.AuthInterceptor;
import com.example.myapp.security.CurrentUserArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Spring MVC 配置：注册鉴权拦截器与 {@code @CurrentUser} 参数解析器。
 *
 * <p>这里**没有** CORS 配置，是有意的：开发期前端通过 Vite 代理访问后端，
 * 浏览器看到的是同源请求。真要跨域部署时再加 CorsRegistry，不要用
 * {@code @CrossOrigin} 在 Controller 上零散地开 —— 那样容易漏配或配宽。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                // 默认放行静态资源一类，但本项目是前后端分离，后端不提供页面；
                // 明确写成 /api/** 更清楚：只保护接口，将来加了 actuator 之类也不会被误拦。
                .addPathPatterns("/api/**")
                // 白名单：这两个接口必须在未登录时可访问，否则会陷入
                // 「要登录得先有令牌，要令牌得先登录」的死循环。
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/health"
                );
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
