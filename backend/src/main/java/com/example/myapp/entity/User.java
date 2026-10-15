package com.example.myapp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应表 sys_user（见 sql/init.sql）。
 *
 * <p>createdAt / updatedAt 由数据库的 DEFAULT CURRENT_TIMESTAMP 与
 * ON UPDATE CURRENT_TIMESTAMP 维护，因此这里不加填充注解；MyBatis-Plus 默认
 * 的 NOT_NULL 插入策略会把 null 字段排除在 INSERT 之外，数据库默认值因而生效。
 *
 * <p><b>不要把这个实体直接当接口返回值</b>，它带着 passwordHash。对外一律用
 * {@link com.example.myapp.dto.UserVO}。
 */
@Data
@TableName("sys_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /**
     * BCrypt 密码哈希（含盐，非明文）。
     *
     * <p>@JsonIgnore 是第二道防线：真正的防线是「接口只返回 UserVO」。
     * 一旦有人图省事把实体塞进响应体，这里能保证哈希不会被序列化出去 ——
     * 反序列化不受影响，登录查库取哈希照常可用。
     */
    @JsonIgnore
    private String passwordHash;

    private String email;

    private Integer age;

    /** 只读：数据库写入 */
    private LocalDateTime createdAt;

    /** 只读：数据库写入 */
    private LocalDateTime updatedAt;

    /** 逻辑删除标记，0=正常 1=已删除；@TableLogic 让查询自动带上 deleted = 0 */
    @TableLogic
    private Integer deleted;
}
