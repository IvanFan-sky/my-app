package com.example.myapp.service;

import com.example.myapp.dto.LoginForm;
import com.example.myapp.dto.LoginResponse;
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
}
