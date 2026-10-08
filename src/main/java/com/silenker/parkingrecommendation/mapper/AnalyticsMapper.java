package com.silenker.parkingrecommendation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 数据分析 Mapper — 统计查询专用
 */
@Mapper
public interface AnalyticsMapper {

    /**
     * 按状态统计车位数量
     * 返回: [{status: "available", count: 10}, {status: "reserved", count: 3}, ...]
     */
    List<Map<String, Object>> countByStatus();

    /**
     * 按车位类型统计各状态数量
     * 返回: [{spot_type: "regular", status: "available", count: 5}, ...]
     */
    List<Map<String, Object>> countByTypeAndStatus();

    /**
     * 统计指定天数内的预约数量和预估收入
     * 返回: [{date: "2025-01-15", count: 8, revenue: 240.0}, ...]
     */
    List<Map<String, Object>> countReservationsByDay(@Param("days") int days);

    /**
     * 统计各状态预约总数
     * 返回: [{status: "confirmed", count: 12}, {status: "pending", count: 3}, ...]
     */
    List<Map<String, Object>> countReservationsByStatus();
}
