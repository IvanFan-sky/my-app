package com.example.myapp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.myapp.common.ApiResponse;
import com.example.myapp.common.AuditAction;
import com.example.myapp.common.UserRole;
import com.example.myapp.dto.AuditLogVO;
import com.example.myapp.security.RequireRole;
import com.example.myapp.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 审计日志查询，路径前缀 /api/audit-logs。
 *
 * <p>**整组只对管理员开放**：日志里有全部用户的操作轨迹与来源 IP，
 * 普通用户能看到别人在做什么本身就是信息泄露。
 */
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@RequireRole(UserRole.ADMIN)
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * GET /api/audit-logs?keyword=&action=&current=1&size=20
     *
     * <p>按时间倒序：审计的第一诉求是「最近发生了什么」。
     */
    @GetMapping
    public ApiResponse<Page<AuditLogVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(auditLogService.pageLogs(keyword, action, current, size));
    }

    /**
     * GET /api/audit-logs/actions —— 可选的动作清单。
     *
     * <p>给前端筛选下拉用。从后端取而不是在前端写死：
     * 后端加了新动作，前端不用改代码就能显示出来。
     */
    @GetMapping("/actions")
    public ApiResponse<List<Map<String, String>>> actions() {
        List<Map<String, String>> list = Arrays.stream(AuditAction.values())
                .map(a -> Map.of("value", a.name(), "label", a.getLabel()))
                .toList();
        return ApiResponse.ok(list);
    }
}
