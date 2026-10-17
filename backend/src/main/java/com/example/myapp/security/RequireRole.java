package com.example.myapp.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明访问某个接口所需的角色。
 *
 * <p><b>为什么用注解而不是在配置里写路径</b>：
 * 之前的做法是在 {@code WebMvcConfig} 里写死 {@code /api/users/**} 挂管理员拦截器。
 * 那种写法有两个问题：
 * <ol>
 *   <li><b>权限和端点分居两处</b>。新增一个管理模块时，只写 Controller 不会生效，
 *       必须记得回配置文件加一行 —— 漏一次就是越权，而且这种漏很难在 review 里看出来。</li>
 *   <li><b>无法表达更细的粒度</b>。同一个 Controller 里如果有「列表要管理员、详情要本人」，
 *       路径通配符表达不了。</li>
 * </ol>
 * 注解把权限声明放在端点自身旁边，读 Controller 就能看到谁能访问，不会漏。
 *
 * <p>用法：
 * <pre>{@code
 * @RequireRole(UserRole.ADMIN)          // 类上：整组接口都要求管理员
 * public class SomeAdminController { ... }
 *
 * @RequireRole({UserRole.ADMIN, UserRole.AUDITOR})   // 多个角色任一即可
 * public ApiResponse<?> report() { ... }
 * }</pre>
 *
 * <p>规则：
 * <ul>
 *   <li>方法上的注解**优先于**类上的 —— 便于整组收紧、个别放开。</li>
 *   <li>没有任何注解 = 只要登录即可（登录态本身仍由 {@link AuthInterceptor} 保证）。</li>
 *   <li>{@code value} 为空数组等同于「任意已登录用户」，写出来只是表达「这里确实考虑过权限」。</li>
 * </ul>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /** 允许访问的角色，见 {@link com.example.myapp.common.UserRole}。留空表示任意已登录用户 */
    String[] value() default {};
}
