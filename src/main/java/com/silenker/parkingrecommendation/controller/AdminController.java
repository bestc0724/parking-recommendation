package com.silenker.parkingrecommendation.controller;

import com.silenker.parkingrecommendation.model.dto.UpdateSpotStatusDTO;
import com.silenker.parkingrecommendation.model.entity.ParkingSpot;
import com.silenker.parkingrecommendation.model.entity.Reservation;
import com.silenker.parkingrecommendation.service.IReservationService;
import com.silenker.parkingrecommendation.service.ISpotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端接口
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ISpotService spotService;
    private final IReservationService reservationService;

    /**
     * 获取所有车位列表
     */
    @GetMapping("/spots")
    public ResponseEntity<List<ParkingSpot>> getAllSpots() {
        return ResponseEntity.ok(spotService.getAllSpots());
    }

    /**
     * 更新车位状态
     */
    @PutMapping("/spots/{id}/status")
    public ResponseEntity<Map<String, Object>> updateSpotStatus(
            @PathVariable String id,
            @RequestBody UpdateSpotStatusDTO dto) {
        boolean success = spotService.updateStatus(id, dto.getStatus());
        if (success) {
            return ResponseEntity.ok(Map.of("status", "updated", "spotId", id));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "更新失败，车位不存在"));
    }

    /**
     * 查看待处理冲突（待处理预约）
     */
    @GetMapping("/conflicts")
    public ResponseEntity<List<Reservation>> getConflicts() {
        return ResponseEntity.ok(reservationService.getPendingReservations());
    }

    /**
     * 处理异常（将预约标记为已处理）
     */
    @PostMapping("/conflicts/{id}/resolve")
    public ResponseEntity<Map<String, Object>> resolveConflict(@PathVariable String id) {
        try {
            reservationService.cancel(id);
            return ResponseEntity.ok(Map.of("status", "resolved", "reservationId", id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
