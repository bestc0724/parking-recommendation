package com.silenker.parkingrecommendation.core.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

/**
 * 计费引擎（纯逻辑，无外部依赖，可单测）
 * <p>
 * 计费规则：
 * - 停车费 = 计费小时数 × 车位单价；不足 1 小时按 1 小时计
 * - 充电费 = 充电度数 × 1.2 元/度
 * - 总金额 = 停车费 + 充电费
 */
public class BillingCalculator {

    /** 充电单价（元/度） */
    public static final BigDecimal CHARGING_RATE = new BigDecimal("1.20");

    /** 最小计费时长：1 小时 */
    public static final int MIN_BILLING_HOURS = 1;

    private BillingCalculator() {
    }

    /**
     * 计算计费小时数：ceil(分钟/60)，最低 1 小时
     */
    public static int calculateBillingHours(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return MIN_BILLING_HOURS;
        }
        long minutes = duration.toMinutes();
        if (minutes <= 0) {
            return MIN_BILLING_HOURS;
        }
        return (int) Math.ceil(minutes / 60.0);
    }

    /**
     * 停车费 = 计费小时数 × 单价
     */
    public static BigDecimal calculateParkingFee(Duration duration, BigDecimal pricePerHour) {
        if (pricePerHour == null) {
            return BigDecimal.ZERO;
        }
        int hours = calculateBillingHours(duration);
        return pricePerHour.multiply(BigDecimal.valueOf(hours))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 充电费 = 度数 × 1.2 元/度
     */
    public static BigDecimal calculateChargingFee(BigDecimal chargingKwh) {
        if (chargingKwh == null || chargingKwh.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return chargingKwh.multiply(CHARGING_RATE)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 总金额 = 停车费 + 充电费
     */
    public static BigDecimal calculateTotal(BigDecimal parkingFee, BigDecimal chargingFee) {
        BigDecimal p = parkingFee != null ? parkingFee : BigDecimal.ZERO;
        BigDecimal c = chargingFee != null ? chargingFee : BigDecimal.ZERO;
        return p.add(c);
    }
}
