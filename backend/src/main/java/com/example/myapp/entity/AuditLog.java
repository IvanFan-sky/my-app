package com.example.myapp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志，对应表 {@code sys_audit_log}（见 Flyway V3）。
 *
 * <p>两条与普通业务表不同的约定：
 * <ul>
 *   <li><b>没有 updated_at、没有 deleted</b>：日志只增不改、不做逻辑删除。
 *       能被修改的日志就失去了审计价值。</li>
 *   <li><b>用户名冗余存字符串</b>：只存 id 的话，用户被改名或删除后，
 *       历史会变成一堆读不懂的孤儿 id。审计记录的应该是「当时发生了什么」，
 *       不是「现在这个 id 指向谁」。</li>
 * </ul>
 */
@Data
@TableName("sys_audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人 id；登录失败时可能为空（用户可能根本不存在） */
    private Long actorId;

    /** 操作人用户名（冗余，防用户被删后读不懂） */
    private String actorUsername;

    /** 动作，取值来自 {@link com.example.myapp.common.AuditAction} */
    private String action;

    /** 对象类型，目前只有 USER */
    private String targetType;

    private Long targetId;

    /** 对象用户名（冗余） */
    private String targetUsername;

    /**
     * 补充信息（JSON 字符串）。
     *
     * <p>**绝不能放密码**：改密码只记「改过」，不记新旧值。
     * 改资料只记改了哪些字段名，不记字段值（值可能是邮箱等个人信息）。
     */
    private String detail;

    private String ip;

    /** 是否成功：0=失败 1=成功 */
    private Integer success;

    /**
     * 发生时间，由数据库默认值生成。
     *
     * <p>与应用层的 createdAt 同理：标记 NEVER，避免 updateById 把它显式写回。
     * 这里虽然不会有更新操作，但保持一致的写法更不容易在将来被误改。
     */
    @TableField(insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}
