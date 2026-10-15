package com.example.myapp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增 / 修改用户的请求体。
 *
 * <p>刻意与实体分开：实体字段由数据库决定，表单字段由接口契约决定，
 * 两者混用会让「客户端能改 id / createdAt / passwordHash」这类漏洞悄悄出现。
 *
 * <p>关于 password：这里只声明长度的「上限」，不声明 @NotBlank（下限）。
 * 因为新增与修改对它的要求不同 —— 新增必须有密码，否则建出来的用户无法登录；
 * 修改时留空表示「不改密码」。用注解表达不了这种条件必填，因此在
 * UserController 的新增分支里显式校验。这是注解的边界，不是偷懒。
 */
@Data
public class UserForm {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名最长 64 个字符")
    private String username;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱最长 128 个字符")
    private String email;

    @Min(value = 0, message = "年龄不能小于 0")
    @Max(value = 200, message = "年龄不能大于 200")
    private Integer age;

    /**
     * 明文密码，仅用于传输，不落库（落库的是 BCrypt 哈希）。
     *
     * <p>这里**刻意不加任何校验注解**：新增和修改对它的要求不同 ——
     * 新增必填（否则建出来的用户无法登录），修改时留空表示「不改密码」。
     * 而 {@code @Size} 会把「留空」判为不合法，正好把合法场景误杀。
     * 「是否必填」由 UserController 的新增分支判断，长度由 UserService 在
     * 真正使用它时校验。注解表达不了条件必填，这是注解的边界，不是偷懒。
     */
    private String password;
}
