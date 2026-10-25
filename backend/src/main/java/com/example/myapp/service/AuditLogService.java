package com.example.myapp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.example.myapp.common.AuditAction;
import com.example.myapp.dto.AuditLogVO;
import com.example.myapp.entity.AuditLog;
import com.example.myapp.security.CurrentUser;

import java.util.Map;

/**
 * 审计日志。
 *
 * <p><b>写入绝不能影响业务</b>：这条链路上任何异常都只记 WARN 然后吞掉。
 * 理由很实在 —— 日志是「观测」手段，不该成为业务失败的原因。
 * 反过来说，这也意味着**日志可能丢**（数据库写失败时），审计要求极高的场景
 * 应该改成同步落盘/消息队列，那是另一套设计。
 */
public interface AuditLogService extends IService<AuditLog> {

    /**
     * 记一条审计日志。
     *
     * @param action         动作
     * @param actorId        操作人 id，可为 null（登录失败时用户可能不存在）
     * @param actorUsername  操作人用户名，可为 null
     * @param targetId       对象 id，可为 null
     * @param targetUsername 对象用户名，可为 null
     * @param detail         补充说明，由服务层序列化成 JSON。
     *                       **不要放密码等敏感值**；改资料只记改了哪些字段名，不记字段值。
     * @param success        是否成功
     */
    void record(AuditAction action,
                Long actorId, String actorUsername,
                Long targetId, String targetUsername,
                Map<String, Object> detail, boolean success);

    /**
     * 记一条审计日志，操作人取当前登录用户。
     *
     * <p><b>「管理员对别人动手」的写操作必须用这个重载</b>，不要手写
     * {@code record(action, null, null, ...)}：漏传一次，那条历史就只剩对象、
     * 没有操作人，事后无法回答「谁删了他」—— 而这正是审计存在的理由。
     *
     * <p>登录失败等匿名场景仍用上面那个重载：那时可能连用户都不存在，
     * 强行传 CurrentUser 反而会写出一个错误的主体。
     *
     * @param actor 当前登录用户；为 null 时等同于匿名操作
     */
    default void record(AuditAction action, CurrentUser actor,
                        Long targetId, String targetUsername,
                        Map<String, Object> detail, boolean success) {
        record(action,
                actor == null ? null : actor.id(),
                actor == null ? null : actor.username(),
                targetId, targetUsername, detail, success);
    }

    /**
     * 分页查询，按时间倒序。
     *
     * @param keyword 模糊过滤操作人或对象用户名（前缀匹配，能用上索引思路与用户列表一致）
     * @param action  动作过滤，为空表示全部
     */
    Page<AuditLogVO> pageLogs(String keyword, String action, long current, long size);
}
