package com.silenker.parkingrecommendation.service.impl;

import com.silenker.parkingrecommendation.core.billing.BillingCalculator;
import com.silenker.parkingrecommendation.mapper.OrderMapper;
import com.silenker.parkingrecommendation.mapper.ParkingSpotMapper;
import com.silenker.parkingrecommendation.mapper.ReservationMapper;
import com.silenker.parkingrecommendation.model.entity.Order;
import com.silenker.parkingrecommendation.model.entity.ParkingSpot;
import com.silenker.parkingrecommendation.model.entity.Reservation;
import com.silenker.parkingrecommendation.service.IOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 订单服务实现
 * 业务闭环: 确认到场 → 开单(parking) → 离场结算(completed) → 支付(paid)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements IOrderService {

    private final OrderMapper orderMapper;
    private final ReservationMapper reservationMapper;
    private final ParkingSpotMapper parkingSpotMapper;

    @Override
    @Transactional
    public Order createFromReservation(String reservationId) {
        Reservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            throw new RuntimeException("预约不存在: " + reservationId);
        }
        if (!"confirmed".equals(reservation.getStatus())) {
            throw new RuntimeException("预约尚未确认到场，无法开单: " + reservation.getStatus());
        }

        // 幂等：已开单直接返回
        Order existing = orderMapper.selectActiveByReservationId(reservationId);
        if (existing != null) {
            return existing;
        }

        Order order = new Order();
        order.setOrderId("O" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setReservationId(reservationId);
        order.setSpotId(reservation.getSpotId());
        order.setUserId(reservation.getUserId());
        order.setStatus("parking");
        order.setEnteredAt(LocalDateTime.now());
        order.setParkingFee(BigDecimal.ZERO);
        order.setChargingFee(BigDecimal.ZERO);
        order.setTotalAmount(BigDecimal.ZERO);
        order.setPaymentStatus("unpaid");
        orderMapper.insert(order);

        // 车位: reserved → occupied（车辆实际占用中）
        parkingSpotMapper.updateStatusForce(reservation.getSpotId(), "occupied");

        log.info("Order created (parking): {} for reservation {} at spot {}",
                order.getOrderId(), reservationId, order.getSpotId());
        return order;
    }

    @Override
    @Transactional
    public Order checkout(String reservationId, BigDecimal chargingKwh) {
        Order order = orderMapper.selectActiveByReservationId(reservationId);
        if (order == null) {
            throw new RuntimeException("没有进行中的停车订单: " + reservationId);
        }

        ParkingSpot spot = parkingSpotMapper.selectById(order.getSpotId());
        BigDecimal pricePerHour = spot != null ? spot.getPricePerHour() : BigDecimal.ZERO;

        LocalDateTime now = LocalDateTime.now();
        Duration duration = Duration.between(order.getEnteredAt(), now);

        // 计费引擎（纯逻辑）
        BigDecimal parkingFee = BillingCalculator.calculateParkingFee(duration, pricePerHour);
        BigDecimal chargingFee = BillingCalculator.calculateChargingFee(chargingKwh);
        BigDecimal total = BillingCalculator.calculateTotal(parkingFee, chargingFee);

        order.setStatus("completed");
        order.setExitedAt(now);
        order.setParkingFee(parkingFee);
        order.setChargingFee(chargingFee);
        order.setTotalAmount(total);
        orderMapper.updateCheckout(order);

        // 释放车位
        parkingSpotMapper.updateStatusForce(order.getSpotId(), "available");

        log.info("Order checked out: {}, parking={}h fee={}, charging={}kwh fee={}, total={}",
                order.getOrderId(), BillingCalculator.calculateBillingHours(duration),
                parkingFee, chargingKwh, chargingFee, total);
        return order;
    }

    @Override
    @Transactional
    public Order pay(String orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在: " + orderId);
        }
        if (!"completed".equals(order.getStatus())) {
            throw new RuntimeException("订单未完成结算，无法支付: " + order.getStatus());
        }
        if ("paid".equals(order.getPaymentStatus())) {
            throw new RuntimeException("订单已支付，请勿重复支付: " + orderId);
        }

        LocalDateTime now = LocalDateTime.now();
        orderMapper.updatePayment(orderId, "paid", now);
        order.setPaymentStatus("paid");
        order.setPaidAt(now);

        log.info("Order paid: {}, amount={}", orderId, order.getTotalAmount());
        return order;
    }

    @Override
    public boolean hasActiveOrder(String reservationId) {
        return orderMapper.selectActiveByReservationId(reservationId) != null;
    }

    @Override
    public List<Order> getUserOrders(String userId) {
        return orderMapper.selectByUserId(userId);
    }
}
