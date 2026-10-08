package com.silenker.parkingrecommendation.service.impl;

import com.silenker.parkingrecommendation.mapper.ParkingSpotMapper;
import com.silenker.parkingrecommendation.mapper.ReservationMapper;
import com.silenker.parkingrecommendation.model.entity.ParkingSpot;
import com.silenker.parkingrecommendation.model.entity.Reservation;
import com.silenker.parkingrecommendation.service.IReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements IReservationService {

    private final ReservationMapper reservationMapper;
    private final ParkingSpotMapper parkingSpotMapper;

    private static final int LOCK_MINUTES = 60;

    /**
     * 预约车位 → 状态为 pending，开始超时计时
     */
    @Override
    @Transactional
    public Reservation reserve(String userId, String spotId) {
        ParkingSpot spot = parkingSpotMapper.selectById(spotId);
        if (spot == null) {
            throw new RuntimeException("车位不存在: " + spotId);
        }
        if (!"available".equals(spot.getStatus())) {
            throw new RuntimeException("车位不可用: " + spotId + ", 当前状态: " + spot.getStatus());
        }

        Reservation existing = reservationMapper.selectActiveBySpotId(spotId);
        if (existing != null) {
            throw new RuntimeException("车位已被预约: " + spotId);
        }

        // 乐观锁锁定车位
        int affected = parkingSpotMapper.updateStatus(spotId, "reserved", spot.getVersion());
        if (affected == 0) {
            throw new RuntimeException("预约冲突，车位状态已变更，请重新选择");
        }

        // 创建预约记录，状态为 pending
        Reservation reservation = new Reservation();
        reservation.setReservationId("R" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        reservation.setSpotId(spotId);
        reservation.setUserId(userId);
        reservation.setStatus("pending");
        reservation.setReservedAt(LocalDateTime.now());
        reservation.setExpiresAt(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
        reservationMapper.insert(reservation);

        log.info("Reservation created (pending): {} for spot {} by user {}", reservation.getReservationId(), spotId, userId);
        return reservation;
    }


    /**
     * 确认到场 → pending → confirmed，取消超时
     */
    @Override
    @Transactional
    public boolean confirmArrival(String reservationId) {
        Reservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            throw new RuntimeException("预约不存在: " + reservationId);
        }
        if (!"pending".equals(reservation.getStatus())) {
            throw new RuntimeException("当前状态不可确认到场: " + reservation.getStatus());
        }

        // 更新预约状态为 confirmed，清除过期时间
        reservationMapper.updateStatusAndClearExpiry(reservationId, "confirmed");

        log.info("Arrival confirmed: {}", reservationId);
        return true;
    }

    /**
     * 取消预约
     */
    @Override
    @Transactional
    public boolean cancel(String reservationId) {
        Reservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            throw new RuntimeException("预约不存在: " + reservationId);
        }
        if (!"confirmed".equals(reservation.getStatus()) && !"pending".equals(reservation.getStatus())) {
            throw new RuntimeException("预约状态不可取消: " + reservation.getStatus());
        }

        reservationMapper.updateStatus(reservationId, "cancelled");
        parkingSpotMapper.updateStatusForce(reservation.getSpotId(), "available");

        log.info("Reservation cancelled: {}", reservationId);
        return true;
    }

    @Override
    public List<Reservation> getUserReservations(String userId) {
        return reservationMapper.selectByUserId(userId);
    }

    @Override
    public List<Reservation> getPendingReservations() {
        return reservationMapper.selectPending();
    }
}
