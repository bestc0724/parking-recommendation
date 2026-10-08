package com.silenker.parkingrecommendation.core.prediction;

import com.silenker.parkingrecommendation.model.entity.OccupancySnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 占用率预测器单元测试（纯逻辑）
 */
class OccupancyPredictorTest {

    private OccupancySnapshot snapshot(LocalDateTime time, double rate) {
        OccupancySnapshot s = new OccupancySnapshot();
        s.setSnapshotTime(time);
        s.setTotalCount(100);
        s.setOccupancyRate(BigDecimal.valueOf(rate));
        return s;
    }

    @Test
    @DisplayName("空数据返回默认预测（50%，零置信度）")
    void emptyDataGivesDefault() {
        OccupancyPredictor.Prediction p = OccupancyPredictor.predict(
                List.of(), DayOfWeek.MONDAY, 9);
        assertEquals(50.0, p.predictedRate);
        assertEquals(0, p.sampleCount);
        assertEquals(0.0, p.confidence);
    }

    @Test
    @DisplayName("同星期+小时样本≥2：取均值，置信度随样本数增长")
    void sameGroupAveraged() {
        List<OccupancySnapshot> snapshots = new ArrayList<>();
        // 3 个「周一 9 点」样本: 80, 70, 60 → 均值 70
        snapshots.add(snapshot(LocalDateTime.of(2026, 9, 7, 9, 0), 80.0));   // 周一
        snapshots.add(snapshot(LocalDateTime.of(2026, 9, 14, 9, 30), 70.0)); // 周一
        snapshots.add(snapshot(LocalDateTime.of(2026, 9, 21, 9, 0), 60.0));  // 周一
        // 干扰样本：周二 9 点（不应参与）
        snapshots.add(snapshot(LocalDateTime.of(2026, 9, 8, 9, 0), 20.0));

        OccupancyPredictor.Prediction p = OccupancyPredictor.predict(
                snapshots, DayOfWeek.MONDAY, 9);

        assertEquals(70.0, p.predictedRate, 0.01);
        assertEquals(3, p.sampleCount);
        assertEquals(0.3, p.confidence, 0.001); // 3/10
    }

    @Test
    @DisplayName("样本不足2个：退化为全量均值，低置信度")
    void insufficientSamplesFallBackToOverallAverage() {
        List<OccupancySnapshot> snapshots = new ArrayList<>();
        snapshots.add(snapshot(LocalDateTime.of(2026, 9, 7, 9, 0), 90.0));  // 周一 9点，唯一同组样本
        snapshots.add(snapshot(LocalDateTime.of(2026, 9, 8, 10, 0), 30.0)); // 周二 10点
        snapshots.add(snapshot(LocalDateTime.of(2026, 9, 9, 11, 0), 60.0)); // 周三 11点

        OccupancyPredictor.Prediction p = OccupancyPredictor.predict(
                snapshots, DayOfWeek.MONDAY, 9);

        // 全量均值 = (90+30+60)/3 = 60
        assertEquals(60.0, p.predictedRate, 0.01);
        assertEquals(1, p.sampleCount);
        assertEquals(0.1, p.confidence, 0.001);
    }

    @Test
    @DisplayName("置信度随样本数饱和（≥10 个样本时为 1.0）")
    void confidenceSaturatesAtTenSamples() {
        List<OccupancySnapshot> snapshots = new ArrayList<>();
        // 2026-01-05 是周一，连续 12 个周一的 18 点样本
        LocalDateTime monday = LocalDateTime.of(2026, 1, 5, 18, 0);
        for (int i = 0; i < 12; i++) {
            snapshots.add(snapshot(monday.plusWeeks(i), 60.0));
        }
        OccupancyPredictor.Prediction p = OccupancyPredictor.predict(
                snapshots, DayOfWeek.MONDAY, 18);
        assertEquals(1.0, p.confidence, 0.001);
        assertEquals(12, p.sampleCount);
    }

    @Test
    @DisplayName("不同星期互不串扰")
    void weekdaysAreSeparated() {
        List<OccupancySnapshot> snapshots = new ArrayList<>();
        snapshots.add(snapshot(LocalDateTime.of(2026, 9, 5, 12, 0), 20.0)); // 周六 12点
        snapshots.add(snapshot(LocalDateTime.of(2026, 9, 12, 12, 0), 30.0)); // 周六 12点
        // 查询周三 12点 → 无同组样本 → 全量均值 25
        OccupancyPredictor.Prediction p = OccupancyPredictor.predict(
                snapshots, DayOfWeek.WEDNESDAY, 12);
        assertEquals(25.0, p.predictedRate, 0.01);
        assertEquals(0, p.sampleCount);
    }
}
