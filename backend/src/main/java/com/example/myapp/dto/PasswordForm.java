package com.example.myapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改自己的密码。
 *
 * <p>把 newPassword 的长度规则写在这里，而**不复用** UserForm 的写法：
 * 那条规则是「新增必填、修改可空」，与这里的「新旧都要填」语义不同，
 * 强行复用会让校验条件越来越绕。
 *
 * <p>注意 {@code oldPassword} 校验失败时**不能**返回 4010：
 * 前端拦截器见到 4010 会清登录态并跳登录页，用户只是打错旧密码就被踢出去，
 * 体验极差。这里用 4000（参数类错误）。
 */
@Data
public class PasswordForm {

    @NotBlank(message = "请输入原密码")
    private String oldPassword;

    @NotBlank(message = "请输入新密码")
    @Size(min = 6, max = 72, message = "新密码长度需在 6~72 个字符之间")
    private String newPassword;
}
