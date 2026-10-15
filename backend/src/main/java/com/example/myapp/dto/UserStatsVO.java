package com.example.myapp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户管理的概览统计。
 *
 * <p>缓存相关字段（{@code cacheHitCount} / {@code cacheMissCount} / {@code cacheHitRate}）
 * 来自进程内的计数器，**重启后端会归零**。它不是持久化指标，用途是让人一眼看出
 * 「缓存到底有没有在起作用」—— 加缓存最怕的是配了但一次都没命中，还以为是数据量小。
 *
 * <p>这里刻意**没有**做「今日新增 / 今日登录」这类统计：前者需要按 created_at 做范围聚合，
 * 后者需要一张登录流水表或 last_login 字段，都属于新需求，不该混在缓存改造里顺手加。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsVO {

    /** 未删除的用户总数（@TableLogic 保证自动排除已删除的） */
    private long totalUsers;

    /** 缓存命中次数（自进程启动起） */
    private long cacheHitCount;

    /** 缓存未命中次数（自进程启动起） */
    private long cacheMissCount;

    /** 命中率，0~1，保留 4 位小数的百分比值由前端格式化 */
    private double cacheHitRate;
}
