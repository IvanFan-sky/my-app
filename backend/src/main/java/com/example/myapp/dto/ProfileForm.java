package com.example.myapp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人中心「修改我的资料」请求体。
 *
 * <p><b>字段是刻意白名单的</b>。这里**没有** username / role / password / deleted：
 * <ul>
 *   <li>username 是登录标识，改它要处理唯一键冲突与令牌里的 sub，属独立需求；</li>
 *   <li>role 是权限字段，能通过个人资料改角色 = 任意用户可自行提权，必须堵死；</li>
 *   <li>password 走独立的修改密码接口，要求验旧密码，不能混在资料里。</li>
 * </ul>
 * 因为 DTO 里根本没有这些字段，客户端即使塞进请求体也不会被应用
 * （Jackson 默认忽略未知属性）—— 这比「收到后再判断要不要用」更难写错。
 *
 * <p>另外，本接口的 userId **只从令牌取**，不接受路径参数，
 * 所以不存在「改 id 就改到别人资料」的越权。
 */
@Data
public class ProfileForm {

    /** 显示名。选填；传空串表示清除（前端会转成 undefined/null） */
    @Size(max = 64, message = "显示名最长 64 个字符")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱最长 128 个字符")
    private String email;

    @Size(max = 255, message = "个人简介最长 255 个字符")
    private String bio;

    @Min(value = 0, message = "年龄不能小于 0")
    @Max(value = 200, message = "年龄不能大于 200")
    private Integer age;
}
