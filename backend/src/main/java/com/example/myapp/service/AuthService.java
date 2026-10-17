package com.example.myapp.service;

import com.example.myapp.dto.LoginForm;
import com.example.myapp.dto.LoginResponse;
import com.example.myapp.dto.PasswordForm;
import com.example.myapp.dto.ProfileForm;
import com.example.myapp.dto.RegisterForm;
import com.example.myapp.dto.UserVO;

/**
 * 认证业务接口。
 *
 * <p>不继承 IService：认证不是对某张表的 CRUD，硬套 IService 只会把
 * 「查用户」和「校验口令」两件事混在一起。
 */
public interface AuthService {

    /**
     * 校验用户名口令并签发令牌。
     *
     * <p>失败时抛 {@link com.example.myapp.common.BizException}，
     * 错误码为 4010，且「用户不存在」与「密码错误」返回**完全相同**的文案。
     *
     * @param form 登录表单（已通过 @Valid 校验）
     * @return 令牌与用户信息
     */
    LoginResponse login(LoginForm form);

    /**
     * 注册新账号，并**直接签发令牌**（注册即登录，不需要再登一次）。
     *
     * <p>返回类型与 {@link #login} 一致，前端两条路径可以用同一套处理逻辑。
     *
     * <p>失败时抛 {@link com.example.myapp.common.BizException}：
     * 用户名已被占用为 4009；密码不合规为 4000（由 UserService 校验字节长度）。
     *
     * @param form 注册表单（已通过 @Valid 校验）
     * @return 令牌与新用户信息
     */
    LoginResponse register(RegisterForm form);

    /**
     * 按主键取当前登录用户的公开信息。
     *
     * <p>用于前端刷新页面后恢复登录态：光有本地存的 token 不够，
     * 还要确认服务端仍认这个用户（可能已被删除）。
     *
     * @param userId             当前登录用户主键，来自令牌
     * @param authenticatedName  令牌里的用户名，仅在用户已被删除时用于日志排查
     * @throws com.example.myapp.common.BizException 用户不存在或已被逻辑删除（4010）
     */
    UserVO getCurrentUser(Long userId, String authenticatedName);

    /**
     * 修改**自己**的资料（显示名 / 邮箱 / 简介 / 年龄）。
     *
     * <p>userId 只能来自令牌，调用方不传路径参数 —— 这是「不可能越权」的结构保证。
     * 可改字段由 {@link ProfileForm} 白名单限定，username / role / password 不在其中。
     *
     * @param userId 当前登录用户主键（来自令牌）
     * @param form   资料表单（已通过 @Valid）
     * @return 更新后的用户信息（含新的 updatedAt）
     * @throws com.example.myapp.common.BizException 用户不存在（4010）
     */
    UserVO updateProfile(Long userId, ProfileForm form);

    /**
     * 修改**自己**的密码，必须先验原密码。
     *
     * <p>失败语义：
     * <ul>
     *   <li>原密码不正确 → 4000。**刻意不用 4010** —— 前端见到 4010 会登出，
     *       输错一次旧密码就被踢出去体验很差；</li>
     *   <li>新密码不合规（长度 / BCrypt 72 字节上限）→ 4000，由 UserService 校验；</li>
     *   <li>新密码与原密码相同 → 4000。</li>
     * </ul>
     *
     * <p><b>已知限制</b>：改密码后，别处已签发的旧令牌在过期前仍然有效。
     * 无状态 JWT 无法主动失效，除非引入 Redis 黑名单，属独立需求。
     *
     * @param userId 当前登录用户主键（来自令牌）
     * @param form   新旧密码（已通过 @Valid）
     */
    void changePassword(Long userId, PasswordForm form);
}
