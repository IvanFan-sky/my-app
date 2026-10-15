package com.example.myapp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应表 sys_user（见 sql/init.sql）。
 *
 * <p>createdAt / updatedAt 由数据库的 DEFAULT CURRENT_TIMESTAMP 与
 * ON UPDATE CURRENT_TIMESTAMP 维护，因此这里不加填充注解；MyBatis-Plus 默认
 * 的 NOT_NULL 插入策略会把 null 字段排除在 INSERT 之外，数据库默认值因而生效。
 */
@Data
@TableName("sys_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

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
