package com.silenker.parkingrecommendation.controller;

import com.silenker.parkingrecommendation.model.dto.*;
import com.silenker.parkingrecommendation.model.entity.ParkingSpot;
import com.silenker.parkingrecommendation.model.entity.Reservation;
import com.silenker.parkingrecommendation.model.entity.User;
import com.silenker.parkingrecommendation.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 司机端接口
 */
@RestController
@RequestMapping("/api/driver")
@RequiredArgsConstructor
public class DriverController {

    private final INluService nluService;
    private final IRuleEngineService ruleEngineService;
    private final IRecommenderService recommenderService;
    private final IExplainerService explainerService;
    private final ISpotService spotService;
    private final IReservationService reservationService;
    private final IUserService userService;
    private final IOrderService orderService;

    /**
     * 提交口语需求，返回解析结果
     */
    @PostMapping("/request")
    public ResponseEntity<Map<String, Object>> submitRequest(@RequestBody DriverRequestDTO request) {
        ParsedConditionsDTO parsed = nluService.parse(request.getQuery());

        Map<String, Object> response = new HashMap<>();
        response.put("requestId", "REQ" + System.currentTimeMillis());
        response.put("parsed", parsed);
        response.put("status", "pending_confirmation");
        return ResponseEntity.ok(response);
    }

    /**
     * 确认解析结果，返回推荐列表
     */
    @PutMapping("/parse/confirm")
    public ResponseEntity<Map<String, Object>> confirmParse(@RequestBody ConfirmParseDTO confirm) {
        // 构建确认后的条件
        ParsedConditionsDTO conditions = new ParsedConditionsDTO();
        conditions.setHardConditions(confirm.getHardConditions());
        // 如果用户未指定软偏好，使用默认排序：价格最低优先，其次距电梯近
        if (confirm.getSoftPreferences() != null && !confirm.getSoftPreferences().isEmpty()) {
            conditions.setSoftPreferences(confirm.getSoftPreferences());
        } else {
            conditions.setSoftPreferences(getDefaultSoftPreferences());
        }
        conditions.setConfidence(1.0);

        // 获取用户信息
        User user = userService.getById(confirm.getUserId());
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户不存在"));
        }

        // Step 1: 获取可用车位
        List<ParkingSpot> available = spotService.getAvailableSpots();

        // Step 2: 规则过滤
        List<ParkingSpot> filtered = ruleEngineService.filter(available, conditions, user);

        // Step 3: AI 排序
        List<RecommendResultDTO> recommendations = recommenderService.recommend(filtered, conditions);

        // Step 4: AI 生成推荐理由
        String query = "用户确认的条件"; // 简化处理
        for (RecommendResultDTO rec : recommendations) {
            String reason = explainerService.generateExplanation(rec, conditions, query);
            rec.setReason(reason);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("recommendations", recommendations);
        response.put("totalCandidates", filtered.size());
        response.put("status", filtered.isEmpty() ? "no_match" : "success");
        return ResponseEntity.ok(response);
    }

    /**
     * 获取推荐（简化版，直接用车位列表 + 用户ID）
     */
    @GetMapping("/recommend")
    public ResponseEntity<Map<String, Object>> getRecommend(@RequestParam String userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户不存在"));
        }

        // 使用默认条件：价格最低优先，其次距电梯近
        ParsedConditionsDTO conditions = new ParsedConditionsDTO();
        conditions.setSoftPreferences(getDefaultSoftPreferences());

        List<ParkingSpot> available = spotService.getAvailableSpots();
        List<ParkingSpot> filtered = ruleEngineService.filter(available, conditions, user);
        List<RecommendResultDTO> recommendations = recommenderService.recommend(filtered, conditions);

        for (RecommendResultDTO rec : recommendations) {
            rec.setReason(String.format("车位 %s，限高%.1f米，距电梯%d米，%.1f元/小时",
                    rec.getSpotId(), rec.getHeightLimit(), rec.getDistanceToElevator(), rec.getPricePerHour()));
        }

        return ResponseEntity.ok(Map.of("recommendations", recommendations));
    }

    /**
     * 预约车位
     */
    @PostMapping("/reserve")
    public ResponseEntity<Map<String, Object>> reserve(@RequestBody ReserveRequestDTO request) {
        try {
            Reservation reservation = reservationService.reserve(request.getUserId(), request.getSpotId());
            return ResponseEntity.ok(Map.of(
                    "reservationId", reservation.getReservationId(),
                    "status", reservation.getStatus(),
                    "expiresAt", reservation.getExpiresAt().toString()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 确认到场（pending → confirmed），自动开立停车订单（parking）
     */
    @PutMapping("/reserve/{id}/confirm")
    public ResponseEntity<Map<String, Object>> confirmArrival(@PathVariable String id) {
        try {
            reservationService.confirmArrival(id);
            var order = orderService.createFromReservation(id);
            return ResponseEntity.ok(Map.of(
                    "status", "confirmed",
                    "orderId", order.getOrderId(),
                    "message", "已确认到场，停车订单已开立，计时开始"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 取消预约（已产生停车订单的预约需先结算离场）
     */
    @DeleteMapping("/reserve/{id}")
    public ResponseEntity<Map<String, Object>> cancelReservation(@PathVariable String id) {
        try {
            if (orderService.hasActiveOrder(id)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "该预约已确认到场并产生停车订单，请先结算离场"));
            }
            reservationService.cancel(id);
            return ResponseEntity.ok(Map.of("status", "cancelled"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 查询用户预约列表
     */
    @GetMapping("/reservations")
    public ResponseEntity<?> getReservations(@RequestParam String userId) {
        return ResponseEntity.ok(reservationService.getUserReservations(userId));
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginDTO dto) {
        try {
            User user = userService.login(dto);
            return ResponseEntity.ok(Map.of(
                    "userId", user.getUserId(),
                    "username", user.getUsername(),
                    "role", user.getRole() != null ? user.getRole() : "driver"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterDTO dto) {
        try {
            User user = userService.register(dto);
            return ResponseEntity.ok(Map.of(
                    "userId", user.getUserId(),
                    "username", user.getUsername(),
                    "message", "注册成功"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 默认软偏好：价格最低优先，其次距电梯近
     */
    private List<ParsedConditionsDTO.SoftPreference> getDefaultSoftPreferences() {
        ParsedConditionsDTO.SoftPreference price = new ParsedConditionsDTO.SoftPreference();
        price.setType("price");
        price.setWeight(0.6);
        price.setSource("default");

        ParsedConditionsDTO.SoftPreference distance = new ParsedConditionsDTO.SoftPreference();
        distance.setType("distance_to_elevator");
        distance.setWeight(0.4);
        distance.setSource("default");

        return List.of(price, distance);
    }
}
