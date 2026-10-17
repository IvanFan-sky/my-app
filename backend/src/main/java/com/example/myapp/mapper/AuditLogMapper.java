package com.example.myapp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.myapp.entity.AuditLog;

/**
 * 审计日志 Mapper。
 *
 * <p>只有单表查询，用 BaseMapper 的条件构造器足够，不需要 XML。
 */
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
