package com.silenker.parkingrecommendation.service.impl;

import com.silenker.parkingrecommendation.model.dto.ParsedConditionsDTO;
import com.silenker.parkingrecommendation.model.dto.RecommendResultDTO;
import com.silenker.parkingrecommendation.service.IExplainerService;
import lombok.extern.slf4j.Slf4j;

/**
 * 模板版推荐理由生成器（不调AI，秒级返回）
 * 用于测试环境或AI不可用时的降级方案
 */
@Slf4j
public class TemplateExplainerService implements IExplainerService {

    @Override
    public String generateExplanation(RecommendResultDTO recommendation,
                                       ParsedConditionsDTO conditions,
                                       String originalQuery) {
        StringBuilder sb = new StringBuilder();

        // 硬条件
        if (conditions.getHardConditions() != null && !conditions.getHardConditions().isEmpty()) {
            sb.append("硬条件全部满足");
            for (ParsedConditionsDTO.HardCondition hc : conditions.getHardConditions()) {
                sb.append("（");
                switch (hc.getType()) {
                    case "height" -> sb.append("限高").append(recommendation.getHeightLimit())
                            .append("米 ≥ 要求").append(hc.getValue()).append("米");
                    case "charging" -> sb.append("配备充电桩");
                    case "spot_type" -> sb.append("类型为").append(spotTypeName(recommendation.getSpotType()));
                    default -> sb.append(hc.getType()).append("符合要求");
                }
                sb.append("）");
            }
            sb.append("。");
        } else {
            sb.append("无特殊硬性要求。");
        }

        // 软偏好
        if (recommendation.getScoreDetails() != null && conditions.getSoftPreferences() != null) {
            boolean hasPreference = false;
            for (ParsedConditionsDTO.SoftPreference pref : conditions.getSoftPreferences()) {
                Double score = recommendation.getScoreDetails().get(pref.getType());
                if (score == null) continue;
                if (!hasPreference) { sb.append("偏好方面："); hasPreference = true; }
                switch (pref.getType()) {
                    case "price" -> {
                        if (score >= 0.7) sb.append("价格较低（").append(recommendation.getPricePerHour()).append("元/时），");
                        else if (score >= 0.4) sb.append("价格适中（").append(recommendation.getPricePerHour()).append("元/时），");
                        else sb.append("价格偏高（").append(recommendation.getPricePerHour()).append("元/时），");
                    }
                    case "distance_to_elevator" -> {
                        int dist = recommendation.getDistanceToElevator();
                        if (dist <= 10) sb.append("距电梯很近（").append(dist).append("米），");
                        else if (dist <= 20) sb.append("距电梯较近（").append(dist).append("米），");
                        else if (dist <= 35) sb.append("距电梯一般（").append(dist).append("米），");
                        else sb.append("距电梯较远（").append(dist).append("米），");
                    }
                    default -> sb.append(pref.getType()).append("得分").append(String.format("%.0f%%", score * 100)).append("，");
                }
            }
            if (hasPreference) { sb.setLength(sb.length() - 1); sb.append("。"); }
        }

        sb.append("综合评分").append(String.format("%.2f", recommendation.getScore())).append("分。");
        return sb.toString();
    }

    private String spotTypeName(String type) {
        if (type == null) return "标准";
        return switch (type) {
            case "standard" -> "标准车位";
            case "large" -> "大型车位";
            case "accessible", "disabled" -> "无障碍车位";
            default -> type;
        };
    }
}
