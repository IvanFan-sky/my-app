package com.example.myapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求体。
 *
 * <p>密码上只做「不能为空 + 有个上限」，**刻意不校验长度下限**。
 * 登录接口不该复用注册时的密码强度规则：一旦调整强度规则，
 * 老用户会在登录时被自己的旧密码挡住，而且错误信息还会泄露密码策略。
 * 口令是否正确，只由 AuthService 用 BCrypt 判断。
 */
@Data
public class LoginForm {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名最长 64 个字符")
    private String username;

    /** 上限 72 只是防超大请求体，不是密码强度要求 */
    @NotBlank(message = "密码不能为空")
    @Size(max = 72, message = "密码最长 72 个字符")
    private String password;
}
