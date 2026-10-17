package com.example.myapp.common;

/**
 * 用户角色。
 *
 * <p>为什么用 String 常量而不是 enum：
 * <ul>
 *   <li>实体字段直接映射数据库的 VARCHAR 列，不需要 MyBatis 的枚举类型处理器配置，
 *       少一层「为什么这个 enum 存进去变成了名字/序号」的隐晦行为；</li>
 *   <li>库里 SELECT 出来就是 {@code ADMIN} / {@code USER}，排查问题不用查字典表。</li>
 * </ul>
 *
 * <p>代价是失去了编译期类型检查，所以**所有赋值处都必须过 {@link #isValid}**：
 * 尤其是管理接口新增/修改用户时，role 来自请求体，不校验就等于允许写入任意字符串。
 */
public final class UserRole {

    /** 管理员：可访问 /api/users/** 的整组管理接口 */
    public static final String ADMIN = "ADMIN";

    /** 普通用户：只能访问个人中心相关接口 */
    public static final String USER = "USER";

    /** 默认角色。数据库列默认值也是 USER，两处必须一致 */
    public static final String DEFAULT_ROLE = USER;

    private UserRole() {
    }

    /** 白名单校验。null 视为不合法 —— 缺省应由调用方显式填 {@link #DEFAULT_ROLE} */
    public static boolean isValid(String role) {
        return ADMIN.equals(role) || USER.equals(role);
    }

    /** 规范化：null / 空白按默认角色处理，其余原样返回（**不**做大小写纠正，避免掩盖输入错误） */
    public static String normalize(String role) {
        return (role == null || role.isBlank()) ? DEFAULT_ROLE : role;
    }
}
