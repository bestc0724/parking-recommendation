package com.silenker.parkingrecommendation.service;

import java.util.Map;

/**
 * 车位需求预测服务
 */
public interface IPredictionService {

    /** 记录一次当前占用率快照（由定时任务调用） */
    void recordSnapshot();

    /** 预测指定小时的占用率（目标小时已过则预测明天同时段） */
    Map<String, Object> predict(int hour);
}
