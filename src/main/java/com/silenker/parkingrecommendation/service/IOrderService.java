package com.silenker.parkingrecommendation.service;

import com.silenker.parkingrecommendation.model.entity.Order;

import java.math.BigDecimal;
import java.util.List;

/**
 * 停车订单服务 — 完整业务闭环：
 * 确认到场开单(parking) → 离场结算(completed) → 支付(paid)
 */
public interface IOrderService {

    /** 确认到场后开单（幂等：已存在进行中订单则直接返回） */
    Order createFromReservation(String reservationId);

    /** 离场结算：计算停车费/充电费，释放车位，订单 completed */
    Order checkout(String reservationId, BigDecimal chargingKwh);

    /** 支付订单：unpaid → paid */
    Order pay(String orderId);

    /** 该预约是否存在进行中的停车订单 */
    boolean hasActiveOrder(String reservationId);

    List<Order> getUserOrders(String userId);
}
