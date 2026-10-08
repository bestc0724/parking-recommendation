package com.silenker.parkingrecommendation.controller;

import com.silenker.parkingrecommendation.model.dto.*;
import com.silenker.parkingrecommendation.model.entity.Order;
import com.silenker.parkingrecommendation.model.entity.ParkingSpot;
import com.silenker.parkingrecommendation.model.entity.Reservation;
import com.silenker.parkingrecommendation.model.entity.User;
import com.silenker.parkingrecommendation.model.entity.Vehicle;
import com.silenker.parkingrecommendation.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * 内部 API — 供 Python LangChain Agent / MCP Server 调用
 * <p>
 * 与 DriverController 的区别：
 * 1. /recommend 合并了 NLU解析 + 确认 + 过滤 + 排序 + 解释 为一步完成
 * 2. 返回更结构化的 JSON（含 status 字段），便于 Agent 判断下一步行动
 * 3. 统计接口供 Analytics Agent 使用
 */
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalController {

    private final INluService nluService;
    private final IRuleEngineService ruleEngineService;
    private final IRecommenderService recommenderService;
    private final IExplainerService explainerService;
    private final ISpotService spotService;
    private final IReservationService reservationService;
    private final IUserService userService;
    private final IAnalyticsService analyticsService;
    private final IOrderService orderService;
    private final IVehicleService vehicleService;
    private final IPredictionService predictionService;

    // ======================== 推荐 ========================

    /**
     * 完整推荐流水线：自然语言 → NLU解析 → 过滤 → 排序 → 解释（一步完成）
     * 可选 vehicleId：用户未在话语中说明限高/充电需求时，自动从车辆档案补全
     */
    @PostMapping("/recommend")
    public ResponseEntity<Map<String, Object>> recommend(@RequestBody Map<String, String> request) {
        String query = request.get("query");
        String userId = request.get("userId");
        String vehicleId = request.get("vehicleId");

        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "query 不能为空"));
        }

        // Step 1: NLU 解析
        ParsedConditionsDTO parsed = nluService.parse(query);
        if (parsed == null) {
            return ResponseEntity.ok(Map.of(
                    "status", "parse_failed",
                    "error", "无法理解您的需求，请换一种方式描述"
            ));
        }

        // Step 1.5: 车辆档案补全（无感推荐）
        // 安全相关（限高）→ 自动补为硬条件；便利相关（充电）→ 自动补为软偏好
        Vehicle vehicle = null;
        if (vehicleId != null && !vehicleId.isBlank()) {
            vehicle = vehicleService.getById(vehicleId);
        }
        if (vehicle != null) {
            boolean hasHeightCondition = parsed.getHardConditions() != null && parsed.getHardConditions().stream()
                    .anyMatch(c -> "height".equals(c.getType()));
            if (!hasHeightCondition && vehicle.getVehicleHeight() != null) {
                ParsedConditionsDTO.HardCondition hc = new ParsedConditionsDTO.HardCondition();
                hc.setType("height");
                hc.setValue(vehicle.getVehicleHeight().toPlainString());
                hc.setUnit("m");
                hc.setSource("vehicle");
                if (parsed.getHardConditions() == null) {
                    parsed.setHardConditions(new ArrayList<>());
                }
                parsed.getHardConditions().add(hc);
            }

            boolean hasChargingCondition = (parsed.getHardConditions() != null && parsed.getHardConditions().stream()
                    .anyMatch(c -> "charging".equals(c.getType())))
                    || (parsed.getSoftPreferences() != null && parsed.getSoftPreferences().stream()
                            .anyMatch(p -> "charging".equals(p.getType())));
            if (!hasChargingCondition && Boolean.TRUE.equals(vehicle.getIsEv())) {
                ParsedConditionsDTO.SoftPreference sp = new ParsedConditionsDTO.SoftPreference();
                sp.setType("charging");
                sp.setWeight(0.3);
                sp.setSource("vehicle");
                if (parsed.getSoftPreferences() == null) {
                    parsed.setSoftPreferences(new ArrayList<>());
                }
                parsed.getSoftPreferences().add(sp);
            }
        }

        // 置信度过低时，返回解析结果让 Agent 确认
        if (parsed.getConfidence() != null && parsed.getConfidence() < 0.6) {
            return ResponseEntity.ok(Map.of(
                    "status", "needs_confirmation",
                    "parsed", parsed,
                    "message", "我对您的需求理解不太确定，请确认以下解析结果"
            ));
        }

        // Step 2: 获取用户信息
        User user = null;
        if (userId != null && !userId.isBlank()) {
            user = userService.getById(userId);
        }

        // Step 3: 获取可用车位
        List<ParkingSpot> available = spotService.getAvailableSpots();
        if (available.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "status", "no_available",
                    "message", "当前没有可用车位"
            ));
        }

        // Step 4: 硬条件过滤
        List<ParkingSpot> filtered = ruleEngineService.filter(available, parsed, user);
        if (filtered.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "status", "no_match",
                    "totalAvailable", available.size(),
                    "parsed", parsed,
                    "message", "没有符合您条件的车位，您是否愿意放宽某些条件？"
            ));
        }

        // Step 5: 软偏好排序
        List<RecommendResultDTO> recommendations = recommenderService.recommend(filtered, parsed);

        // Step 6: 生成推荐理由
        for (RecommendResultDTO rec : recommendations) {
            String reason = explainerService.generateExplanation(rec, parsed, query);
            rec.setReason(reason);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "success");
        result.put("recommendations", recommendations);
        result.put("totalCandidates", filtered.size());
        result.put("totalAvailable", available.size());
        result.put("parsed", parsed);
        if (vehicle != null) {
            result.put("vehicleApplied", Map.of(
                    "vehicleId", vehicle.getVehicleId(),
                    "plateNumber", vehicle.getPlateNumber(),
                    "vehicleHeight", vehicle.getVehicleHeight(),
                    "isEv", vehicle.getIsEv()
            ));
        }
        return ResponseEntity.ok(result);
    }

    // ======================== 预约管理 ========================

    /**
     * 预约车位
     */
    @PostMapping("/reserve")
    public ResponseEntity<Map<String, Object>> reserve(@RequestBody Map<String, String> request) {
        String userId = request.get("userId");
        String spotId = request.get("spotId");

        if (userId == null || spotId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId 和 spotId 不能为空"));
        }

        try {
            Reservation reservation = reservationService.reserve(userId, spotId);
            return ResponseEntity.ok(Map.of(
                    "status", "reserved",
                    "reservationId", reservation.getReservationId(),
                    "spotId", spotId,
                    "expiresAt", reservation.getExpiresAt().toString(),
                    "message", "预约成功！请在1小时内到达车位并确认到场"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("status", "error", "error", e.getMessage()));
        }
    }

    /**
     * 确认到场
     */
    @PutMapping("/reserve/{id}/confirm")
    public ResponseEntity<Map<String, Object>> confirmArrival(@PathVariable String id) {
        try {
            reservationService.confirmArrival(id);
            return ResponseEntity.ok(Map.of(
                    "status", "confirmed",
                    "reservationId", id,
                    "message", "已确认到场，祝您停车愉快！"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("status", "error", "error", e.getMessage()));
        }
    }

    /**
     * 取消预约
     * 注意：已确认到场并产生停车订单的预约不可直接取消，需先结算离场
     */
    @DeleteMapping("/reserve/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelReservation(@PathVariable String id) {
        try {
            if (orderService.hasActiveOrder(id)) {
                return ResponseEntity.ok(Map.of(
                        "status", "error",
                        "error", "该预约已确认到场并产生停车订单，请先结算离场（checkout）再取消"
                ));
            }
            reservationService.cancel(id);
            return ResponseEntity.ok(Map.of(
                    "status", "cancelled",
                    "reservationId", id,
                    "message", "预约已取消"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("status", "error", "error", e.getMessage()));
        }
    }

    // ======================== 查询 ========================

    /**
     * 查询可用车位
     */
    @GetMapping("/spots/available")
    public ResponseEntity<Map<String, Object>> getAvailableSpots() {
        List<ParkingSpot> spots = spotService.getAvailableSpots();
        return ResponseEntity.ok(Map.of(
                "total", spots.size(),
                "spots", spots
        ));
    }

    /**
     * 查询全部车位（含各状态）
     */
    @GetMapping("/spots")
    public ResponseEntity<Map<String, Object>> getAllSpots() {
        List<ParkingSpot> spots = spotService.getAllSpots();
        return ResponseEntity.ok(Map.of(
                "total", spots.size(),
                "spots", spots
        ));
    }

    /**
     * 查询用户预约记录
     */
    @GetMapping("/reservations")
    public ResponseEntity<Map<String, Object>> getReservations(@RequestParam String userId) {
        List<Reservation> reservations = reservationService.getUserReservations(userId);
        return ResponseEntity.ok(Map.of(
                "total", reservations.size(),
                "reservations", reservations
        ));
    }

    // ======================== 停车订单 ========================

    /**
     * 离场结算：计算停车费/充电费，释放车位，订单 → completed
     */
    @PostMapping("/order/checkout")
    public ResponseEntity<Map<String, Object>> checkout(@RequestBody Map<String, String> request) {
        String reservationId = request.get("reservationId");
        if (reservationId == null || reservationId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "reservationId 不能为空"));
        }

        BigDecimal chargingKwh = null;
        try {
            if (request.get("chargingKwh") != null && !request.get("chargingKwh").isBlank()) {
                chargingKwh = new BigDecimal(request.get("chargingKwh"));
            }
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "chargingKwh 格式非法"));
        }

        try {
            Order order = orderService.checkout(reservationId, chargingKwh);
            return ResponseEntity.ok(Map.of(
                    "status", "completed",
                    "orderId", order.getOrderId(),
                    "parkingFee", order.getParkingFee(),
                    "chargingFee", order.getChargingFee(),
                    "totalAmount", order.getTotalAmount(),
                    "paymentStatus", order.getPaymentStatus(),
                    "message", "结算完成，请支付停车费用 " + order.getTotalAmount() + " 元"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("status", "error", "error", e.getMessage()));
        }
    }

    /**
     * 支付订单：unpaid → paid
     */
    @PostMapping("/order/{id}/pay")
    public ResponseEntity<Map<String, Object>> payOrder(@PathVariable String id) {
        try {
            Order order = orderService.pay(id);
            return ResponseEntity.ok(Map.of(
                    "status", "paid",
                    "orderId", id,
                    "amount", order.getTotalAmount(),
                    "message", "支付成功，感谢您的使用！"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("status", "error", "error", e.getMessage()));
        }
    }

    /**
     * 查询用户订单
     */
    @GetMapping("/orders")
    public ResponseEntity<Map<String, Object>> getOrders(@RequestParam String userId) {
        List<Order> orders = orderService.getUserOrders(userId);
        return ResponseEntity.ok(Map.of(
                "total", orders.size(),
                "orders", orders
        ));
    }

    // ======================== 车辆档案 ========================

    /**
     * 添加车辆档案
     */
    @PostMapping("/vehicles")
    public ResponseEntity<Map<String, Object>> addVehicle(@RequestBody Map<String, Object> request) {
        try {
            String userId = (String) request.get("userId");
            String plateNumber = (String) request.get("plateNumber");
            String model = (String) request.get("model");
            BigDecimal vehicleHeight = request.get("vehicleHeight") != null
                    ? new BigDecimal(request.get("vehicleHeight").toString())
                    : null;
            Boolean isEv = Boolean.parseBoolean(String.valueOf(request.getOrDefault("isEv", "false")));

            Vehicle vehicle = vehicleService.addVehicle(userId, plateNumber, model, vehicleHeight, isEv);
            return ResponseEntity.ok(Map.of(
                    "status", "added",
                    "vehicleId", vehicle.getVehicleId(),
                    "plateNumber", vehicle.getPlateNumber(),
                    "message", "车辆档案添加成功"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "error", e.getMessage()));
        }
    }

    /**
     * 查询用户车辆
     */
    @GetMapping("/vehicles")
    public ResponseEntity<Map<String, Object>> getVehicles(@RequestParam String userId) {
        List<Vehicle> vehicles = vehicleService.getUserVehicles(userId);
        return ResponseEntity.ok(Map.of(
                "total", vehicles.size(),
                "vehicles", vehicles
        ));
    }

    // ======================== 数据分析 ========================

    /**
     * 停车场整体占用率统计
     */
    @GetMapping("/stats/occupancy")
    public ResponseEntity<Map<String, Object>> getOccupancyStats() {
        return ResponseEntity.ok(analyticsService.getOccupancyStats());
    }

    /**
     * 收入统计（最近 N 天）
     */
    @GetMapping("/stats/revenue")
    public ResponseEntity<Map<String, Object>> getRevenueReport(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(analyticsService.getRevenueReport(days));
    }

    /**
     * 车位需求预测（指定小时，目标时间已过则预测明天同时段）
     */
    @GetMapping("/stats/predict")
    public ResponseEntity<Map<String, Object>> predictOccupancy(@RequestParam int hour) {
        try {
            return ResponseEntity.ok(predictionService.predict(hour));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
