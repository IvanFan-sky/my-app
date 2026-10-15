package com.example.myapp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求体。
 *
 * <p>为什么不复用 {@link UserForm}：两者对 password 的规则**本就不同**。
 * UserForm 的 password 刻意没有长度下限（修改用户时留空表示「不改密码」，
 * 加下限会把留空判为非法）；而注册是新建账号，必须有下限，否则能注册出
 * 一个密码为空字符串的账号。共用同一个 DTO 会让两边互相牵制，
 * 所以宁可多一个小类，也不要为了省一个文件把校验规则搞成「谁都不对」。
 *
 * <p>注册只收「自己填得出来」的字段：没有 age（注册时没人会填年龄，
 * 需要的话登录后到用户管理里补），也不收 id / deleted 这类服务端字段。
 */
@Data
public class RegisterForm {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名最长 64 个字符")
    private String username;

    /**
     * 明文密码，仅用于传输（落库的是 BCrypt 哈希）。
     *
     * <p>下限 6 位：太短的密码对抗暴力破解没有意义。
     * 上限 72 位：BCrypt 只使用前 72 字节，更长的部分会被静默忽略 ——
     * 与其让用户以为自己设了长密码、实际只有前 72 字节生效，不如直接拒绝。
     * 真正的字节数校验在 UserService.assignPassword（中文一个字占 3 字节，
     * 按字符数判断会放过超长输入）。
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 72, message = "密码长度需在 6~72 个字符之间")
    private String password;

    /** 选填。不填就是 null，@Email 对 null 视为通过 */
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱最长 128 个字符")
    private String email;
}
