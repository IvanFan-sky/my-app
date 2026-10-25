package com.example.myapp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.myapp.common.AuditAction;
import com.example.myapp.common.SqlLike;
import com.example.myapp.dto.AuditLogVO;
import com.example.myapp.entity.AuditLog;
import com.example.myapp.mapper.AuditLogMapper;
import com.example.myapp.service.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl extends ServiceImpl<AuditLogMapper, AuditLog>
        implements AuditLogService {

    /** 用来把 detail 序列化成 JSON。手动拼字符串在值含引号/中文时会产出非法 JSON */
    private final ObjectMapper objectMapper;

    /** detail 字段在 V3 里是 VARCHAR(1000)，超长会被数据库截断或报错，这里主动截断 */
    private static final int DETAIL_MAX_LENGTH = 1000;

    @Override
    public void record(AuditAction action,
                       Long actorId, String actorUsername,
                       Long targetId, String targetUsername,
                       Map<String, Object> detail, boolean success) {
        // 整段包 try/catch：审计写失败不能让「登录成功」变成「登录失败」
        try {
            AuditLog entry = new AuditLog();
            entry.setAction(action.name());
            entry.setActorId(actorId);
            entry.setActorUsername(trimToNull(actorUsername));
            // 目前只有用户这一类对象；将来加别的模块时再扩展
            entry.setTargetType(targetId == null ? null : "USER");
            entry.setTargetId(targetId);
            entry.setTargetUsername(trimToNull(targetUsername));
            entry.setDetail(serializeDetail(detail));
            entry.setIp(currentIp());
            entry.setSuccess(success ? 1 : 0);
            save(entry);
        } catch (Exception e) {
            log.warn("写审计日志失败（不影响业务）action={} actor={} target={}: {}",
                    action, actorUsername, targetUsername, e.getMessage());
        }
    }

    @Override
    public Page<AuditLogVO> pageLogs(String keyword, String action, long current, long size) {
        String kw = StringUtils.hasText(keyword) ? SqlLike.escape(keyword.trim()) : null;
        String act = StringUtils.hasText(action) ? action.trim() : null;

        Page<AuditLog> page = page(new Page<>(current, size),
                Wrappers.<AuditLog>lambdaQuery()
                        // 前缀匹配，与用户列表保持同一套语义。
                        // 审计日志通常按「某个人」或「某个对象」查，前缀够用且能用上索引。
                        .and(kw != null, w -> w
                                .likeRight(AuditLog::getActorUsername, kw)
                                .or()
                                .likeRight(AuditLog::getTargetUsername, kw))
                        .eq(act != null, AuditLog::getAction, act)
                        // 最新在前：审计的第一诉求是「最近发生了什么」
                        .orderByDesc(AuditLog::getId));

        Page<AuditLogVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(AuditLogVO::from).toList());
        return result;
    }

    /**
     * 取来源 IP。
     *
     * <p>只认 {@code getRemoteAddr()}，**不解析 X-Forwarded-For**：
     * 那个头是客户端可伪造的，没配可信代理时解析它等于让攻击者随意填写来源。
     * 真正部署在反向代理后面时，应改为读 Spring 的
     * {@code ForwardedHeaderFilter} 处理后的值（或配置 server.forward-headers-strategy）。
     */
    private static String currentIp() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            String ip = request.getRemoteAddr();
            return truncate(ip, 64);
        } catch (Exception e) {
            // 非 Web 线程（比如定时任务）里没有请求上下文，忽略即可
            return null;
        }
    }

    /** 把 detail 序列化成 JSON；失败时回落成 toString，绝不让它把审计写挂 */
    private String serializeDetail(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        try {
            return truncate(objectMapper.writeValueAsString(detail));
        } catch (Exception e) {
            return truncate(String.valueOf(detail));
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= DETAIL_MAX_LENGTH ? value : value.substring(0, DETAIL_MAX_LENGTH);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
