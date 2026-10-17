package com.example.myapp.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应表 sys_user（结构见 Flyway 迁移 `db/migration/V1__init.sql`）。
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
     * 显示名。个人中心可改，**不影响登录**（登录始终用 username）。
     * 允许为 null：未设置时前端回退显示 username。
     */
    private String nickname;

    /**
     * BCrypt 密码哈希（含盐，非明文）。
     *
     * <p>@JsonIgnore 是第二道防线：真正的防线是「接口只返回 UserVO」。
     * 一旦有人图省事把实体塞进响应体，这里能保证哈希不会被序列化出去 ——
     * 反序列化不受影响，登录查库取哈希照常可用。
     */
    @JsonIgnore
    private String passwordHash;

    /**
     * 角色：{@link com.example.myapp.common.UserRole#ADMIN} /
     * {@link com.example.myapp.common.UserRole#USER}。
     *
     * <p>用 String 而不是枚举，原因见 UserRole 的注释。
     * **任何来自请求体的赋值都必须先过 UserRole.isValid()**，否则等于允许写入任意字符串。
     */
    private String role;

    private String email;

    private String bio;

    private Integer age;

    /**
     * 令牌版本号。
     *
     * <p>签发的 JWT 里带着签发时的版本；{@code AuthInterceptor} 比对「令牌里的版本」
     * 与「库里当前版本」，不一致就拒绝。改密码 / 改角色时把它 +1，
     * 于是所有已签发的旧令牌（含 refresh token）立即失效。
     *
     * <p>为什么不用 Redis 黑名单：黑名单要为每个令牌存一条、还要处理过期清理，
     * 且 Redis 一清空（重启/误删）撤销就失效了。版本号是每用户一个整数，
     * 存在数据库里，天然持久。代价是撤销粒度是「全设备」—— 改密码后其他设备也要重登，
     * 这通常正是改密码想要的语义。
     */
    private Integer tokenVersion;

    /**
     * 只读：数据库写入。
     *
     * <p>{@code FieldStrategy.NEVER} 的意思是「INSERT / UPDATE 语句里都不要带这一列」，
     * 完全交给数据库的 DEFAULT CURRENT_TIMESTAMP。
     *
     * <p>不加会怎样（实测踩到）：{@code updateById} 默认的 NOT_NULL 策略发现
     * 实体上 updatedAt 非空（从库里读出来的旧值），就把它拼进 UPDATE 的 SET 子句，
     * 于是显式写入旧时间戳，**反而把数据库的 ON UPDATE CURRENT_TIMESTAMP 压掉了** ——
     * 表现为「数据改了但 updated_at 一直不变」。
     *
     * <p>这与 AGENTS.md 「时间字段由数据库维护」的约定一致：
     * 不是让应用接管时间，而是让应用**不去碰**它。
     */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    /** 只读：数据库写入，规则同 createdAt */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;

    /** 逻辑删除标记，0=正常 1=已删除；@TableLogic 让查询自动带上 deleted = 0 */
    @TableLogic
    private Integer deleted;
}
