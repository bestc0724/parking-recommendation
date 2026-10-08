package com.silenker.parkingrecommendation.core.explainer;

import com.silenker.parkingrecommendation.model.dto.ParsedConditionsDTO;
import com.silenker.parkingrecommendation.model.dto.RecommendResultDTO;
import com.silenker.parkingrecommendation.service.IAiService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reason generator for the explanation layer.
 * <p>
 * Builds Java-side facts from recommendation + conditions,
 * constructs a prompt, calls LLM, and post-processes or falls back
 * to a dynamic, fact-based fallback string.
 */
@Slf4j
@Component
public class ReasonGenerator {

    private static final String TIME_DISCLAIMER = "基于当前时刻数据";

    private static final String EXPLAIN_SYSTEM_PROMPT =
            "你是停车推荐解释助手。根据提供的事实生成推荐理由。只能使用提供的事实，不得虚构。输出简洁中文，不超过100字。";

    private final IAiService aiService;

    public ReasonGenerator(IAiService aiService) {
        this.aiService = aiService;
    }

    /**
     * Generate an explanation for a recommendation.
     *
     * @param recommendation the recommended parking spot result
     * @param conditions     the parsed (and confirmed) conditions from the driver
     * @param originalQuery  the original driver query text
     * @return a natural-language explanation string
     */
    public String generate(RecommendResultDTO recommendation,
                           ParsedConditionsDTO conditions,
                           String originalQuery) {
        // Null safety check
        if (recommendation == null || conditions == null) {
            log.warn("generate() called with null recommendation or conditions, returning fallback");
            return "推荐信息不可用。" + TIME_DISCLAIMER + "。";
        }

        // 1. Build facts from Java-side data
        ExplanationFacts facts = buildFacts(recommendation, conditions);

        // 2. Build prompt
        String prompt = buildPrompt(facts, originalQuery);

        // 3. AI trace log (desensitized)
        log.debug("[AI-TRACE] requestId={}, spotId={}, promptVersion=explain-v1, promptLength={}, promptSnippet={}",
                MDC.get("requestId"), facts.spotId, prompt.length(),
                prompt.substring(0, Math.min(200, prompt.length())));

        // 4. Call LLM
        String aiResponse = callLlm(prompt);

        // 4. Process response or fallback
        if (aiResponse != null) {
            return postProcess(aiResponse, facts);
        }
        return buildFallback(facts);
    }

    // ===================== Fact Building =====================

    ExplanationFacts buildFacts(RecommendResultDTO recommendation,
                                ParsedConditionsDTO conditions) {
        ExplanationFacts facts = new ExplanationFacts();

        // Copy basic fields from recommendation
        facts.spotId = recommendation.getSpotId();
        facts.heightLimit = recommendation.getHeightLimit();
        facts.hasCharging = recommendation.getHasCharging();
        facts.spotType = recommendation.getSpotType();
        facts.distanceToElevator = recommendation.getDistanceToElevator();
        facts.pricePerHour = recommendation.getPricePerHour();
        facts.score = recommendation.getScore();
        facts.scoreDetails = recommendation.getScoreDetails();

        // Match hard conditions against recommendation
        facts.matchedHardConditions = new ArrayList<>();
        facts.notSatisfiedConditions = new ArrayList<>();
        facts.unverifiableConditions = new ArrayList<>();

        if (conditions.getHardConditions() != null) {
            for (ParsedConditionsDTO.HardCondition hc : conditions.getHardConditions()) {
                switch (hc.getType()) {
                    case "height" -> {
                        if (hc.getValue() != null && recommendation.getHeightLimit() != null) {
                            try {
                                BigDecimal required = new BigDecimal(hc.getValue());
                                if (recommendation.getHeightLimit().compareTo(required) >= 0) {
                                    facts.matchedHardConditions.add(
                                            "限高" + recommendation.getHeightLimit() + "米满足(需求≤" + hc.getValue() + "米)");
                                } else {
                                    facts.notSatisfiedConditions.add(
                                            "限高不满足(需求≤" + hc.getValue() + "米, 实际" + recommendation.getHeightLimit() + "米)");
                                }
                            } catch (NumberFormatException e) {
                                facts.matchedHardConditions.add("限高条件已通过过滤");
                            }
                        } else {
                            facts.matchedHardConditions.add("限高条件已通过过滤");
                        }
                    }
                    case "charging" -> {
                        if (Boolean.TRUE.equals(recommendation.getHasCharging())) {
                            facts.matchedHardConditions.add("充电桩满足");
                        } else {
                            facts.notSatisfiedConditions.add("充电桩不满足");
                        }
                    }
                    case "spot_type" -> {
                        if (hc.getValue() != null && hc.getValue().equals(recommendation.getSpotType())) {
                            facts.matchedHardConditions.add("车位类型" + recommendation.getSpotType() + "匹配");
                        } else if (recommendation.getSpotType() != null) {
                            facts.matchedHardConditions.add("车位类型" + recommendation.getSpotType() + "已通过过滤");
                        }
                    }
                    case "qualification" -> {
                        facts.unverifiableConditions.add("资格(" + hc.getValue() + ")无法验证");
                    }
                    case "accessibility" -> {
                        facts.unverifiableConditions.add("无障碍需求无法验证");
                    }
                    default -> {
                        facts.matchedHardConditions.add(hc.getType() + "已通过过滤");
                    }
                }
            }
        }

        // Generate preference descriptions from scoreDetails
        facts.preferenceDescriptions = new ArrayList<>();
        if (recommendation.getScoreDetails() != null) {
            Map<String, Double> details = recommendation.getScoreDetails();
            if (details.containsKey("distance_to_elevator") && recommendation.getDistanceToElevator() != null) {
                double distScore = details.get("distance_to_elevator");
                String proximity = distScore > 0.6 ? "较近" : distScore > 0.3 ? "适中" : "较远";
                facts.preferenceDescriptions.add(
                        "距电梯" + recommendation.getDistanceToElevator() + "米(" + proximity + ")");
            }
            if (details.containsKey("price") && recommendation.getPricePerHour() != null) {
                double priceScore = details.get("price");
                String affordability = priceScore > 0.6 ? "偏低" : priceScore > 0.3 ? "适中" : "偏高";
                facts.preferenceDescriptions.add(
                        "价格" + recommendation.getPricePerHour() + "元/小时(" + affordability + ")");
            }
            if (details.containsKey("charging") && Boolean.TRUE.equals(recommendation.getHasCharging())) {
                facts.preferenceDescriptions.add("有充电桩(偏好满足)");
            }
        }

        // Copy NLU metadata
        facts.ambiguousTerms = conditions.getAmbiguousTerms() != null
                ? conditions.getAmbiguousTerms() : new ArrayList<>();
        facts.requiresConfirmation = conditions.getRequiresConfirmation() != null
                ? conditions.getRequiresConfirmation() : new ArrayList<>();
        facts.inferredDefaults = conditions.getInferredDefaults() != null
                ? conditions.getInferredDefaults() : new ArrayList<>();

        // Missing fields
        facts.missingFields = new ArrayList<>();
        if (recommendation.getSpotType() == null) {
            facts.missingFields.add("车位类型:实体无此字段");
        }
        if (recommendation.getDistanceToElevator() == null) {
            facts.missingFields.add("距电梯距离:实体无此字段");
        }

        return facts;
    }

    // ===================== Prompt Building =====================

    String buildPrompt(ExplanationFacts facts, String originalQuery) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是停车推荐解释助手。请根据以下事实生成推荐理由。\n\n");

        sb.append("## 司机需求\n");
        sb.append("\"").append(sanitize(originalQuery)).append("\"\n\n");

        sb.append("## 推荐车位\n");
        sb.append("- 编号：").append(facts.spotId).append("\n");
        sb.append("- 限高：").append(facts.heightLimit != null ? facts.heightLimit + "米" : "未知").append("\n");
        sb.append("- 充电桩：").append(Boolean.TRUE.equals(facts.hasCharging) ? "有" : "无").append("\n");
        sb.append("- 车位类型：").append(facts.spotType != null ? facts.spotType : "未知").append("\n");
        sb.append("- 距电梯：").append(facts.distanceToElevator != null ? facts.distanceToElevator + "米" : "未知").append("\n");
        sb.append("- 价格：").append(facts.pricePerHour != null ? facts.pricePerHour + "元/小时" : "未知").append("\n");
        sb.append("- 得分：").append(facts.score != null ? String.format("%.2f", facts.score) : "未知").append("\n\n");

        // Score details
        if (facts.scoreDetails != null && !facts.scoreDetails.isEmpty()) {
            sb.append("## 排序得分详情\n");
            for (Map.Entry<String, Double> entry : facts.scoreDetails.entrySet()) {
                sb.append("- ").append(entry.getKey()).append(": ")
                        .append(String.format("%.3f", entry.getValue())).append("\n");
            }
            sb.append("\n");
        }

        sb.append("## 硬条件匹配\n");
        if (facts.matchedHardConditions.isEmpty() && facts.notSatisfiedConditions.isEmpty()
                && facts.unverifiableConditions.isEmpty()) {
            sb.append("- 无特殊硬性要求\n");
        } else {
            if (!facts.matchedHardConditions.isEmpty()) {
                sb.append("### 已满足\n");
                for (String mc : facts.matchedHardConditions) {
                    sb.append("- ").append(mc).append("\n");
                }
            }
            if (!facts.notSatisfiedConditions.isEmpty()) {
                sb.append("### 未满足\n");
                for (String ns : facts.notSatisfiedConditions) {
                    sb.append("- ").append(ns).append("\n");
                }
            }
            if (!facts.unverifiableConditions.isEmpty()) {
                sb.append("### 无法验证\n");
                for (String uc : facts.unverifiableConditions) {
                    sb.append("- ").append(uc).append("\n");
                }
            }
        }
        sb.append("\n");

        sb.append("## 软偏好匹配\n");
        if (facts.preferenceDescriptions.isEmpty()) {
            sb.append("- 无特殊偏好\n");
        } else {
            for (String pd : facts.preferenceDescriptions) {
                sb.append("- ").append(pd).append("\n");
            }
        }
        sb.append("\n");

        // Ambiguous / confirmation / inferred
        if (!facts.ambiguousTerms.isEmpty() || !facts.requiresConfirmation.isEmpty()
                || !facts.inferredDefaults.isEmpty()) {
            sb.append("## 不确定项\n");
            for (String at : facts.ambiguousTerms) {
                sb.append("- 模糊表述: ").append(at).append("\n");
            }
            for (String rc : facts.requiresConfirmation) {
                sb.append("- 待确认: ").append(rc).append("\n");
            }
            for (String id : facts.inferredDefaults) {
                sb.append("- 推断默认: ").append(id).append("\n");
            }
            sb.append("\n");
        }

        sb.append("## 规则\n");
        sb.append("1. 只能使用上述事实\n");
        sb.append("2. 不得虚构价格、距离、资格或车位属性\n");
        sb.append("3. 不得把软偏好说成硬条件\n");
        sb.append("4. 不得把无法确认的条件说成已满足\n");
        sb.append("5. 不得重新决定车位是否合格\n");
        sb.append("6. 不超过100字\n");
        sb.append("7. 结尾必须包含\"").append(TIME_DISCLAIMER).append("\"\n");

        return sb.toString();
    }

    // ===================== LLM Call =====================

    String callLlm(String prompt) {
        try {
            String response = aiService.chat(EXPLAIN_SYSTEM_PROMPT, prompt);
            if (response == null || response.isBlank()) {
                log.warn("LLM returned null or blank response for explanation");
                return null;
            }
            log.debug("[AI-TRACE] requestId={}, type=EXPLAIN, responseLength={}, responseSnippet={}",
                    MDC.get("requestId"), response.length(),
                    response.substring(0, Math.min(200, response.length())));
            return response;
        } catch (Exception e) {
            log.error("LLM call failed for explanation: {}", e.getMessage());
            return null;
        }
    }

    // ===================== Post-processing =====================

    String postProcess(String aiResponse, ExplanationFacts facts) {
        String cleaned = stripCodeFences(aiResponse).trim();

        if (cleaned.isEmpty()) {
            log.warn("AI response was empty after stripping code fences");
            return buildFallback(facts);
        }

        // Ensure time disclaimer is present
        if (!cleaned.contains(TIME_DISCLAIMER)) {
            cleaned = cleaned + " " + TIME_DISCLAIMER;
        }

        return cleaned;
    }

    /**
     * Remove markdown code fences (```json ... ```, ``` ... ```) from AI response.
     */
    static String stripCodeFences(String text) {
        if (text == null) {
            return "";
        }
        // Remove opening fence: ```json, ```text, ``` etc.
        String cleaned = text.replaceAll("```\\w*\\s*\\n?", "");
        // Remove closing fence
        cleaned = cleaned.replaceAll("```", "");
        return cleaned.trim();
    }

    // ===================== Dynamic Fallback =====================

    String buildFallback(ExplanationFacts facts) {
        StringBuilder sb = new StringBuilder();
        sb.append("推荐 ").append(facts.spotId).append("：");

        // Describe verified hard conditions
        if (!facts.matchedHardConditions.isEmpty()) {
            sb.append(String.join("，", facts.matchedHardConditions));
        }

        // Describe not-satisfied conditions
        if (!facts.notSatisfiedConditions.isEmpty()) {
            if (!facts.matchedHardConditions.isEmpty()) {
                sb.append("。");
            }
            sb.append(String.join("，", facts.notSatisfiedConditions));
        }

        // Describe preference matches
        if (!facts.preferenceDescriptions.isEmpty()) {
            if (!facts.matchedHardConditions.isEmpty() || !facts.notSatisfiedConditions.isEmpty()) {
                sb.append("。");
            }
            sb.append(String.join("，", facts.preferenceDescriptions));
        }

        // Mark unverifiable conditions
        if (!facts.unverifiableConditions.isEmpty()) {
            sb.append("。部分条件无法确认");
        }

        // Mark missing fields
        if (!facts.missingFields.isEmpty()) {
            sb.append("。部分信息不可用");
        }

        // If no match info at all
        if (facts.matchedHardConditions.isEmpty() && facts.notSatisfiedConditions.isEmpty()
                && facts.unverifiableConditions.isEmpty()
                && facts.preferenceDescriptions.isEmpty()) {
            sb.append("当前车位可用");
        }

        sb.append("。").append(TIME_DISCLAIMER).append("。");
        return sb.toString();
    }

    // ===================== Utility =====================

    /**
     * Sanitize user input for embedding in prompt (treat as data, not instruction).
     */
    private String sanitize(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("[\\x00-\\x1F]", " ").trim();
    }

    // ===================== ExplanationFacts =====================

    @Getter
    public static class ExplanationFacts {
        private String spotId;
        private BigDecimal heightLimit;
        private Boolean hasCharging;
        private String spotType;
        private Integer distanceToElevator;
        private BigDecimal pricePerHour;
        private Double score;
        private Map<String, Double> scoreDetails;

        private List<String> matchedHardConditions;
        private List<String> notSatisfiedConditions;
        private List<String> unverifiableConditions;

        private List<String> preferenceDescriptions;

        private List<String> ambiguousTerms;
        private List<String> requiresConfirmation;
        private List<String> inferredDefaults;

        private List<String> missingFields;
    }
}
