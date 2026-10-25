package com.example.myapp.config;

import com.example.myapp.security.AuthInterceptor;
import com.example.myapp.security.RoleInterceptor;
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
    private final RoleInterceptor roleInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 顺序很重要：先鉴权（写入 CurrentUser），再授权（读 CurrentUser 判角色）。
        // 反过来 RoleInterceptor 会读不到用户，把管理员也拦下。
        registry.addInterceptor(authInterceptor)
                // 默认放行静态资源一类，但本项目是前后端分离，后端不提供页面；
                // 明确写成 /api/** 更清楚：只保护接口，将来加了 actuator 之类也不会被误拦。
                .addPathPatterns("/api/**")
                // 白名单：这些接口必须在未登录时可访问，否则会陷入
                // 「要登录得先有令牌，要令牌得先登录」的死循环 ——
                // 注册同理，注册本身就是为了拿到第一个令牌。
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register",
                        // 刷新令牌：调用它时 access 往往已过期，必须放行
                        "/api/auth/refresh",
                        "/api/health"
                );

        // 角色校验：挂在 /api/** 上，但**只对带 @RequireRole 的端点生效**。
        // 这样新增接口默认只需登录；要收紧权限时在方法或类上加个注解即可，
        // 不必回这个配置文件加路径 —— 路径硬编码的老写法漏一次就是越权。
        registry.addInterceptor(roleInterceptor)
                .addPathPatterns("/api/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
