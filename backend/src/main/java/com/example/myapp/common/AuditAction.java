package com.example.myapp.common;

/**
 * 审计动作。
 *
 * <p>用枚举而不是散落的字符串常量：动作名会出现在数据库、接口响应、前端筛选下拉里，
 * 三处对不上就会出现「日志里有一条查不到的动作」。枚举是唯一的真相来源。
 *
 * <p>**只登记「写操作 + 登录」**，不登记查询：查询量级完全不同（每次翻页一条），
 * 而审计真正要回答的是「谁改了谁」。
 */
public enum AuditAction {

    /** 登录成功 */
    LOGIN("登录"),

    /** 登录失败（含密码错误、用户不存在、账号锁定） */
    LOGIN_FAIL("登录失败"),

    /** 注册（注册即登录） */
    REGISTER("注册"),

    /** 管理员新增用户 */
    CREATE_USER("新增用户"),

    /** 管理员修改用户（含改角色、重置密码） */
    UPDATE_USER("修改用户"),

    /** 管理员删除用户 */
    DELETE_USER("删除用户"),

    /** 管理员批量删除用户 */
    BATCH_DELETE("批量删除用户"),

    /** 用户修改自己的资料 */
    UPDATE_PROFILE("修改资料"),

    /** 用户修改自己的密码 */
    CHANGE_PASSWORD("修改密码");

    /** 中文标签。放这里而不是前端，避免两边维护同一份映射 */
    private final String label;

    AuditAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
