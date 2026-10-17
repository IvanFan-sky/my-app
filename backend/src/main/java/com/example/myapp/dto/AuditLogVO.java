package com.example.myapp.dto;

import com.example.myapp.common.AuditAction;
import com.example.myapp.entity.AuditLog;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志响应体。
 *
 * <p>{@code actionLabel} 由后端给出中文，不给前端留一份映射表：
 * 两边各维护一份，早晚会出现「后端加了新动作、前端显示成英文枚举名」。
 */
@Data
public class AuditLogVO {

    private Long id;
    private Long actorId;
    private String actorUsername;
    /** 枚举名（前端筛选用它） */
    private String action;
    /** 中文标签（前端展示用它） */
    private String actionLabel;
    private String targetType;
    private Long targetId;
    private String targetUsername;
    private String detail;
    private String ip;
    private Boolean success;
    private LocalDateTime createdAt;

    public static AuditLogVO from(AuditLog entity) {
        if (entity == null) {
            return null;
        }
        AuditLogVO vo = new AuditLogVO();
        vo.setId(entity.getId());
        vo.setActorId(entity.getActorId());
        vo.setActorUsername(entity.getActorUsername());
        vo.setAction(entity.getAction());
        vo.setActionLabel(labelOf(entity.getAction()));
        vo.setTargetType(entity.getTargetType());
        vo.setTargetId(entity.getTargetId());
        vo.setTargetUsername(entity.getTargetUsername());
        vo.setDetail(entity.getDetail());
        vo.setIp(entity.getIp());
        // 数据库是 tinyint，对外给 boolean 更贴合语义
        vo.setSuccess(entity.getSuccess() == null || entity.getSuccess() != 0);
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    /**
     * 把动作名翻成中文。
     *
     * <p>库里存的是字符串，若哪天有人手工插了一条不认识的动作，
     * 这里回落成原名而不是抛异常 —— 审计日志的读取路径不该因为一条脏数据整体失败。
     */
    private static String labelOf(String action) {
        if (action == null) {
            return null;
        }
        try {
            return AuditAction.valueOf(action).getLabel();
        } catch (IllegalArgumentException e) {
            return action;
        }
    }
}
