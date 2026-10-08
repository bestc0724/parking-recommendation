package com.silenker.parkingrecommendation.service.impl;

import com.silenker.parkingrecommendation.mapper.AnalyticsMapper;
import com.silenker.parkingrecommendation.service.IAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements IAnalyticsService {

    private final AnalyticsMapper analyticsMapper;

    @Override
    public Map<String, Object> getOccupancyStats() {
        // 1. 整体统计
        List<Map<String, Object>> statusCounts = analyticsMapper.countByStatus();
        int total = statusCounts.stream()
                .mapToInt(row -> ((Number) row.get("count")).intValue())
                .sum();

        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (Map<String, Object> row : statusCounts) {
            byStatus.put((String) row.get("status"), ((Number) row.get("count")).intValue());
        }

        int available = byStatus.getOrDefault("available", 0);
        int reserved = byStatus.getOrDefault("reserved", 0);
        int occupied = byStatus.getOrDefault("occupied", 0);
        int maintenance = byStatus.getOrDefault("maintenance", 0);
        double occupancyRate = total > 0 ? Math.round((1.0 - (double) available / total) * 1000.0) / 10.0 : 0;

        // 2. 按类型分组统计
        List<Map<String, Object>> typeStatusCounts = analyticsMapper.countByTypeAndStatus();
        Map<String, Map<String, Integer>> byType = new LinkedHashMap<>();
        for (Map<String, Object> row : typeStatusCounts) {
            String type = (String) row.get("spot_type");
            String status = (String) row.get("status");
            int count = ((Number) row.get("count")).intValue();
            byType.computeIfAbsent(type, k -> new LinkedHashMap<>()).put(status, count);
        }

        List<Map<String, Object>> typeDetails = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> entry : byType.entrySet()) {
            Map<String, Integer> statusMap = entry.getValue();
            int typeTotal = statusMap.values().stream().mapToInt(Integer::intValue).sum();
            int typeAvailable = statusMap.getOrDefault("available", 0);
            double typeRate = typeTotal > 0 ? Math.round((1.0 - (double) typeAvailable / typeTotal) * 1000.0) / 10.0 : 0;

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("type", entry.getKey());
            detail.put("total", typeTotal);
            detail.put("available", typeAvailable);
            detail.put("occupancyRate", typeRate);
            detail.put("statusBreakdown", statusMap);
            typeDetails.add(detail);
        }

        // 3. 预约统计
        List<Map<String, Object>> reservationCounts = analyticsMapper.countReservationsByStatus();
        Map<String, Integer> reservationByStatus = new LinkedHashMap<>();
        for (Map<String, Object> row : reservationCounts) {
            reservationByStatus.put((String) row.get("status"), ((Number) row.get("count")).intValue());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalSpots", total);
        result.put("available", available);
        result.put("reserved", reserved);
        result.put("occupied", occupied);
        result.put("maintenance", maintenance);
        result.put("occupancyRate", occupancyRate);
        result.put("byType", typeDetails);
        result.put("reservations", reservationByStatus);
        return result;
    }

    @Override
    public Map<String, Object> getRevenueReport(int days) {
        List<Map<String, Object>> dailyData = analyticsMapper.countReservationsByDay(days);

        double totalRevenue = dailyData.stream()
                .mapToDouble(row -> {
                    Object val = row.get("revenue");
                    return val instanceof Number ? ((Number) val).doubleValue() : 0;
                })
                .sum();

        int totalReservations = dailyData.stream()
                .mapToInt(row -> ((Number) row.get("count")).intValue())
                .sum();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", "最近" + days + "天");
        result.put("totalRevenue", Math.round(totalRevenue * 100.0) / 100.0);
        result.put("totalReservations", totalReservations);
        result.put("dailyBreakdown", dailyData);
        return result;
    }
}
