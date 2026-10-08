package com.silenker.parkingrecommendation.service.impl;

import com.silenker.parkingrecommendation.core.prediction.OccupancyPredictor;
import com.silenker.parkingrecommendation.mapper.OccupancyMapper;
import com.silenker.parkingrecommendation.model.entity.OccupancySnapshot;
import com.silenker.parkingrecommendation.service.IPredictionService;
import com.silenker.parkingrecommendation.service.ISpotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 需求预测服务
 * <p>
 * 数据来源：每 30 分钟的占用率快照（occupancy_snapshots 表）
 * 冷启动：表为空时生成 14 天合成历史数据（按星期/小时的典型模式 + 噪声），
 *         有真实数据后逐步替换（合成数据随时间自然被真实快照稀释）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionServiceImpl implements IPredictionService {

    private static final int TOTAL_SPOTS = 100;
    private static final int SYNTHETIC_DAYS = 14;
    private static final int SNAPSHOTS_PER_DAY = 48; // 每 30 分钟一条

    /** 工作日 24 小时占用率模式（0-1） */
    private static final double[] WEEKDAY_PATTERN = {
            0.15, 0.10, 0.08, 0.06, 0.05, 0.05, 0.08, 0.15,
            0.35, 0.55, 0.65, 0.70, 0.68, 0.60, 0.55, 0.60,
            0.72, 0.85, 0.80, 0.70, 0.55, 0.40, 0.25, 0.18
    };

    /** 周末 24 小时占用率模式（0-1） */
    private static final double[] WEEKEND_PATTERN = {
            0.25, 0.20, 0.15, 0.12, 0.10, 0.10, 0.12, 0.20,
            0.35, 0.50, 0.60, 0.68, 0.70, 0.65, 0.60, 0.58,
            0.60, 0.68, 0.72, 0.65, 0.50, 0.38, 0.30, 0.22
    };

    private final OccupancyMapper occupancyMapper;
    private final ISpotService spotService;

    @Override
    public void recordSnapshot() {
        int total = spotService.getAllSpots().size();
        int available = spotService.getAvailableSpots().size();
        BigDecimal rate = total > 0
                ? BigDecimal.valueOf((1.0 - (double) available / total) * 100)
                        .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        OccupancySnapshot snapshot = new OccupancySnapshot();
        snapshot.setSnapshotTime(LocalDateTime.now());
        snapshot.setAvailableCount(available);
        snapshot.setTotalCount(total);
        snapshot.setOccupancyRate(rate);
        occupancyMapper.insert(snapshot);
        log.debug("Occupancy snapshot recorded: {}/{} ({})", available, total, rate);
    }

    @Override
    public Map<String, Object> predict(int hour) {
        if (hour < 0 || hour > 23) {
            throw new RuntimeException("小时参数非法: " + hour + "（应为 0-23）");
        }

        // 冷启动：无历史数据时生成合成数据
        bootstrapIfEmpty();

        LocalDateTime now = LocalDateTime.now();
        DayOfWeek dow = now.getDayOfWeek();
        if (hour <= now.getHour()) {
            dow = dow.plus(1); // 目标小时已过，预测明天同时段
        }

        List<OccupancySnapshot> snapshots = occupancyMapper.selectAll();
        OccupancyPredictor.Prediction prediction =
                OccupancyPredictor.predict(snapshots, dow, hour);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetDayOfWeek", dow.name());
        result.put("targetHour", hour);
        result.put("predictedRate", prediction.predictedRate);
        result.put("sampleCount", prediction.sampleCount);
        result.put("confidence", prediction.confidence);
        result.put("totalSnapshots", snapshots.size());
        return result;
    }

    /**
     * 冷启动：表为空时生成 14 天合成历史数据
     * 模式：工作日/周末双曲线 + 固定种子的随机噪声（可复现）
     */
    private synchronized void bootstrapIfEmpty() {
        if (occupancyMapper.count() > 0) {
            return;
        }
        log.info("占用率快照表为空，生成 {} 天合成历史数据（冷启动）", SYNTHETIC_DAYS);

        Random random = new Random(42); // 固定种子，结果可复现
        LocalDateTime start = LocalDateTime.now()
                .minusDays(SYNTHETIC_DAYS)
                .withMinute(0).withSecond(0).withNano(0);

        List<OccupancySnapshot> batch = new ArrayList<>();
        for (int day = 0; day < SYNTHETIC_DAYS; day++) {
            for (int halfHour = 0; halfHour < SNAPSHOTS_PER_DAY; halfHour++) {
                LocalDateTime time = start.plusDays(day).plusMinutes(halfHour * 30L);
                double[] pattern = isWeekend(time.getDayOfWeek()) ? WEEKEND_PATTERN : WEEKDAY_PATTERN;
                double base = pattern[time.getHour()];
                double noise = (random.nextDouble() - 0.5) * 0.10; // ±5%
                double rate = Math.max(0.02, Math.min(0.98, base + noise));

                OccupancySnapshot snapshot = new OccupancySnapshot();
                snapshot.setSnapshotTime(time);
                snapshot.setTotalCount(TOTAL_SPOTS);
                int occupied = (int) Math.round(TOTAL_SPOTS * rate);
                snapshot.setAvailableCount(TOTAL_SPOTS - occupied);
                snapshot.setOccupancyRate(BigDecimal.valueOf(rate * 100)
                        .setScale(2, RoundingMode.HALF_UP));
                batch.add(snapshot);
            }
        }

        for (OccupancySnapshot snapshot : batch) {
            occupancyMapper.insert(snapshot);
        }
        log.info("合成历史数据生成完成: {} 条", batch.size());
    }

    private boolean isWeekend(DayOfWeek dow) {
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }
}
