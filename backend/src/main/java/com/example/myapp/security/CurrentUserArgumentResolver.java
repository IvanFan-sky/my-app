package com.example.myapp.security;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 把 {@link AuthInterceptor} 写进请求属性的 {@link CurrentUser} 注入到
 * Controller 方法参数里。
 *
 * <p>找的是 {@code request.getAttribute(ATTRIBUTE_NAME)}，与拦截器写入的 key 一致。
 *
 * <p>参数类型不匹配（比如把 CurrentUser 用在别的类型上）时不在这里报错，
 * 直接抛异常会让错误定位变难；返回 null 交给 Spring 处理即可 ——
 * 实际上注解与类型是配套使用的，写错会在编译期就被发现。
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
                && CurrentUser.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        return webRequest.getAttribute(CurrentUser.ATTRIBUTE_NAME, RequestAttributes.SCOPE_REQUEST);
    }
}
