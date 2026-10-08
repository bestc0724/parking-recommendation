package com.silenker.parkingrecommendation.model.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 占用率快照 — 每 30 分钟记录一次，作为需求预测的训练数据
 */
@Data
public class OccupancySnapshot {
    private Long id;
    private LocalDateTime snapshotTime;
    private Integer availableCount;
    private Integer totalCount;
    private BigDecimal occupancyRate; // 百分比 0-100
}
