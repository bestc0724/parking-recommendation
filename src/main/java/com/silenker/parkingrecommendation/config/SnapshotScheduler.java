package com.silenker.parkingrecommendation.config;

import com.silenker.parkingrecommendation.service.IPredictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 占用率快照定时任务 — 每 30 分钟记录一次，积累需求预测的训练数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotScheduler {

    private final IPredictionService predictionService;

    /**
     * 每小时的第 5 分和第 35 分各记录一次快照
     */
    @Scheduled(cron = "0 5,35 * * * ?")
    public void recordOccupancySnapshot() {
        try {
            predictionService.recordSnapshot();
        } catch (Exception e) {
            log.warn("占用率快照记录失败: {}", e.getMessage());
        }
    }
}
