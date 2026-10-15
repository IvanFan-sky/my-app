package com.example.myapp.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 {@link CurrentUser} 类型的 Controller 方法参数上，
 * 由 {@link CurrentUserArgumentResolver} 注入当前登录用户。
 *
 * <p><b>为什么注解叫 LoginUser 而不是 CurrentUser</b>：Java 允许注解与类型同名，
 * 但这时 {@code CurrentUser.class} 会解析成那个 record 类型而不是注解类型，
 * 于是 {@code parameter.hasParameterAnnotation(CurrentUser.class)} 编译报
 * 「CurrentUser 无法转换为 Annotation」—— 这类同名冲突报错很难一眼看出原因。
 * 名字分开就没有这种歧义。
 *
 * <p>用法：{@code public ApiResponse<UserVO> me(@LoginUser CurrentUser currentUser)}
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoginUser {
}
