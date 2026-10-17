package com.example.myapp.dto;

import com.example.myapp.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户响应体。**接口一律返回它，绝不返回 {@link User} 实体本身。**
 *
 * <p>原因：实体上有 passwordHash。只要有人图省事把实体直接当返回值，
 * 密码哈希就会随列表接口对整个内网可见。用 VO 把「对外字段」和「库表字段」
 * 分开，是防止这类事故最省事的办法 —— 加了新字段也别无脑往这里搬。
 *
 * <p>{@code role} 是**有意暴露**的：前端要靠它隐藏「用户管理」菜单与路由，
 * 否则普通用户点进去必然拿到 403，体验很困惑。
 * 注意前端拿到 role 只是**界面控制**，真正的权限判定永远在后端
 * （见 AdminOnlyInterceptor），前端隐藏菜单不等于安全。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {

    private Long id;
    private String username;
    /** 显示名，未设置时为 null */
    private String nickname;
    /** ADMIN / USER */
    private String role;
    private String email;
    /** 个人简介 */
    private String bio;
    private Integer age;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UserVO from(User user) {
        if (user == null) {
            return null;
        }
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getRole(),
                user.getEmail(), user.getBio(), user.getAge(),
                user.getCreatedAt(), user.getUpdatedAt());
    }
}
