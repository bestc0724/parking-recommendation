package com.silenker.parkingrecommendation.model.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 车辆档案 — 用户绑定车辆信息，实现"无感推荐"（只说"帮我停车"即可）
 */
@Data
public class Vehicle {
    private String vehicleId;
    private String userId;
    private String plateNumber;
    private String model;
    private BigDecimal vehicleHeight; // 车辆高度（米），用于自动补全限高硬条件
    private Boolean isEv;             // 是否新能源车
    private LocalDateTime createdAt;
}
