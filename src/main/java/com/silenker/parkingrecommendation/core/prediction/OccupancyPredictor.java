package com.silenker.parkingrecommendation.core.prediction;

import com.silenker.parkingrecommendation.model.entity.OccupancySnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 占用率预测器（纯逻辑，可单测）
 * <p>
 * 模型：按「星期几 + 小时」分组的移动平均。
 * - 同组历史样本 ≥ 2 时，取均值；置信度随样本数增长（10 个样本即饱和）
 * - 样本不足时退化为全量均值（冷启动策略）
 */
public class OccupancyPredictor {

    /** 置信度饱和所需的最少样本数 */
    public static final int CONFIDENCE_SATURATION_SAMPLES = 10;

    private OccupancyPredictor() {
    }

    public static class Prediction {
        public final double predictedRate;
        public final int sampleCount;
        public final double confidence;

        public Prediction(double predictedRate, int sampleCount, double confidence) {
            this.predictedRate = predictedRate;
            this.sampleCount = sampleCount;
            this.confidence = confidence;
        }
    }

    /**
     * 预测指定「星期几 + 小时」的占用率
     */
    public static Prediction predict(List<OccupancySnapshot> snapshots, DayOfWeek dow, int hour) {
        if (snapshots == null || snapshots.isEmpty()) {
            return new Prediction(50.0, 0, 0.0);
        }

        // 同组样本：相同星期几 + 相同小时
        List<BigDecimal> samples = snapshots.stream()
                .filter(s -> s.getSnapshotTime() != null
                        && s.getSnapshotTime().getDayOfWeek() == dow
                        && s.getSnapshotTime().getHour() == hour)
                .map(OccupancySnapshot::getOccupancyRate)
                .filter(rate -> rate != null)
                .collect(Collectors.toList());

        if (samples.size() >= 2) {
            double sum = samples.stream().mapToDouble(BigDecimal::doubleValue).sum();
            double avg = round1(sum / samples.size());
            double confidence = Math.min(1.0, samples.size() / (double) CONFIDENCE_SATURATION_SAMPLES);
            return new Prediction(avg, samples.size(), confidence);
        }

        // 冷启动退化：全量均值
        double overall = snapshots.stream()
                .map(OccupancySnapshot::getOccupancyRate)
                .filter(rate -> rate != null)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(50.0);
        return new Prediction(round1(overall), samples.size(), 0.1);
    }

    private static double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
