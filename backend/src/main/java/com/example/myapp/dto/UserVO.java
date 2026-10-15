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
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {

    private Long id;
    private String username;
    private String email;
    private Integer age;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UserVO from(User user) {
        if (user == null) {
            return null;
        }
        return new UserVO(user.getId(), user.getUsername(), user.getEmail(), user.getAge(),
                user.getCreatedAt(), user.getUpdatedAt());
    }
}
