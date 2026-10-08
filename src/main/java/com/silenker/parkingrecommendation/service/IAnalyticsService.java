package com.silenker.parkingrecommendation.service;

import java.util.Map;

/**
 * 数据分析服务 — 供 Analytics Agent 调用
 */
public interface IAnalyticsService {

    /**
     * 停车场占用率统计（整体 + 分区域/类型）
     */
    Map<String, Object> getOccupancyStats();

    /**
     * 收入统计（最近 N 天的预约收入）
     */
    Map<String, Object> getRevenueReport(int days);
}
