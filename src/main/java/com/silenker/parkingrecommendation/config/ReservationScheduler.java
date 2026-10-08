package com.silenker.parkingrecommendation.config;

import com.silenker.parkingrecommendation.mapper.ParkingSpotMapper;
import com.silenker.parkingrecommendation.mapper.ReservationMapper;
import com.silenker.parkingrecommendation.model.entity.Reservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 预约超时自动取消定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationScheduler {

    private final ReservationMapper reservationMapper;
    private final ParkingSpotMapper parkingSpotMapper;

    /**
     * 每 30 秒检查一次过期预约
     */
    @Scheduled(fixedRate = 30000)
    @Transactional
    public void cancelExpiredReservations() {
        List<Reservation> expired = reservationMapper.selectExpired();
        if (expired.isEmpty()) {
            return;
        }

        log.info("发现 {} 条过期预约，开始自动取消", expired.size());

        for (Reservation r : expired) {
            // 1. 更新预约状态为 expired
            reservationMapper.updateStatus(r.getReservationId(), "expired");

            // 2. 释放车位
            parkingSpotMapper.updateStatusForce(r.getSpotId(), "available");

            log.info("自动取消预约: {}，释放车位: {}", r.getReservationId(), r.getSpotId());
        }
    }
}
