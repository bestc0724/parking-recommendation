package com.silenker.parkingrecommendation.model.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 停车订单 — 完整停车业务闭环的核心实体
 * 状态机: parking(停车中) → completed(已结算离场)；支付: unpaid → paid
 */
@Data
public class Order {
    private String orderId;
    private String reservationId;
    private String spotId;
    private String userId;
    private String status;          // parking / completed / cancelled
    private LocalDateTime enteredAt;
    private LocalDateTime exitedAt;
    private BigDecimal parkingFee;  // 停车费
    private BigDecimal chargingFee; // 充电费
    private BigDecimal totalAmount; // 总金额
    private String paymentStatus;   // unpaid / paid
    private LocalDateTime paidAt;
}
