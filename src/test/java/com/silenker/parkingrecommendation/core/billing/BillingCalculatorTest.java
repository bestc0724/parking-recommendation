package com.silenker.parkingrecommendation.core.billing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 计费引擎单元测试（纯逻辑）
 */
class BillingCalculatorTest {

    // ==================== 计费小时数 ====================

    @Test
    @DisplayName("不足1小时按1小时计")
    void lessThanOneHourBilledAsOne() {
        assertEquals(1, BillingCalculator.calculateBillingHours(Duration.ofMinutes(1)));
        assertEquals(1, BillingCalculator.calculateBillingHours(Duration.ofMinutes(59)));
    }

    @Test
    @DisplayName("恰好整小时")
    void exactlyOneHour() {
        assertEquals(1, BillingCalculator.calculateBillingHours(Duration.ofHours(1)));
        assertEquals(3, BillingCalculator.calculateBillingHours(Duration.ofHours(3)));
    }

    @Test
    @DisplayName("超过整小时向上取整")
    void partialHourRoundedUp() {
        assertEquals(2, BillingCalculator.calculateBillingHours(Duration.ofMinutes(61)));
        assertEquals(2, BillingCalculator.calculateBillingHours(Duration.ofHours(1).plusMinutes(1)));
        assertEquals(5, BillingCalculator.calculateBillingHours(Duration.ofHours(4).plusMinutes(30)));
    }

    @Test
    @DisplayName("null/零/负时长退化为1小时")
    void degenerateDurationsBilledAsOne() {
        assertEquals(1, BillingCalculator.calculateBillingHours(null));
        assertEquals(1, BillingCalculator.calculateBillingHours(Duration.ZERO));
        assertEquals(1, BillingCalculator.calculateBillingHours(Duration.ofMinutes(-30)));
    }

    // ==================== 停车费 ====================

    @Test
    @DisplayName("停车费 = 计费小时数 × 单价")
    void parkingFeeCalculation() {
        BigDecimal fee = BillingCalculator.calculateParkingFee(
                Duration.ofHours(2).plusMinutes(30), new BigDecimal("5.00"));
        assertEquals(new BigDecimal("15.00"), fee);
    }

    @Test
    @DisplayName("单价为 null 时停车费为 0")
    void nullPriceGivesZeroFee() {
        assertEquals(BigDecimal.ZERO,
                BillingCalculator.calculateParkingFee(Duration.ofHours(2), null));
    }

    // ==================== 充电费 ====================

    @Test
    @DisplayName("充电费 = 度数 × 1.2 元/度")
    void chargingFeeCalculation() {
        assertEquals(new BigDecimal("24.00"),
                BillingCalculator.calculateChargingFee(new BigDecimal("20")));
    }

    @Test
    @DisplayName("未充电（0 度 / null / 负数）充电费为 0")
    void noChargingGivesZero() {
        assertEquals(BigDecimal.ZERO, BillingCalculator.calculateChargingFee(null));
        assertEquals(BigDecimal.ZERO, BillingCalculator.calculateChargingFee(BigDecimal.ZERO));
        assertEquals(BigDecimal.ZERO, BillingCalculator.calculateChargingFee(new BigDecimal("-5")));
    }

    // ==================== 总金额 ====================

    @Test
    @DisplayName("总金额 = 停车费 + 充电费")
    void totalAmount() {
        BigDecimal total = BillingCalculator.calculateTotal(
                new BigDecimal("15.00"), new BigDecimal("24.00"));
        assertEquals(new BigDecimal("39.00"), total);
    }

    @Test
    @DisplayName("金额精度保持两位小数")
    void scaleIsTwo() {
        BigDecimal fee = BillingCalculator.calculateParkingFee(
                Duration.ofMinutes(90), new BigDecimal("3.30"));
        assertEquals(new BigDecimal("6.60"), fee);
        assertEquals(2, fee.scale());
    }
}
