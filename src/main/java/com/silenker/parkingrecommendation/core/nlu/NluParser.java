package com.silenker.parkingrecommendation.core.nlu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silenker.parkingrecommendation.model.dto.ParsedConditionsDTO;
import com.silenker.parkingrecommendation.service.IAiService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NLU 解析器：调用 IAiService 解析自然语言为结构化条件。
 * 负责 AI 输出后处理：JSON 提取、降级处理、字段校验。
 */
@Slf4j
@Component
public class NluParser {

    private static final Set<String> VALID_HARD_TYPES = Set.of(
            "height", "vehicle_length", "vehicle_width", "charging",
            "spot_type", "qualification", "accessibility"
    );

    private static final Set<String> VALID_SOFT_TYPES = Set.of(
            "distance_to_elevator", "distance_to_exit", "price",
            "charging", "convenience", "spot_type"
    );

    private static final int MAX_QUERY_LENGTH = 2000;

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{[\\s\\S]*\"hard_conditions\"[\\s\\S]*\\}");

    private final IAiService aiService;
    private final ObjectMapper objectMapper;

    public NluParser(IAiService aiService, ObjectMapper objectMapper) {
        this.aiService = aiService;
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 解析自然语言查询为结构化条件。
     *
     * @param query 司机原始口语输入
     * @return 结构化解析结果；API 失败时返回低置信度空结果
     */
    public ParsedConditionsDTO parse(String query) {
        if (query == null || query.isBlank()) {
            return buildLowConfidenceResult("empty query");
        }

        if (query.length() > MAX_QUERY_LENGTH) {
            log.warn("Query length {} exceeds max {}, returning low confidence result", query.length(), MAX_QUERY_LENGTH);
            return buildLowConfidenceResult("query too long (" + query.length() + " > " + MAX_QUERY_LENGTH + ")");
        }

        String prompt = PromptTemplates.buildNluPrompt(query);
        log.debug("[AI-TRACE] requestId={}, type=NLU, promptLength={}, queryLength={}",
                MDC.get("requestId"), prompt.length(), query.length());
        String aiResponse;
        try {
            aiResponse = aiService.chat(prompt);
        } catch (Exception e) {
            log.error("IAiService.chat() threw exception for query: {}", query, e);
            return buildLowConfidenceResult("AI service exception");
        }

        if (aiResponse == null || aiResponse.isBlank()) {
            log.warn("AI returned null/empty response for query: {}", query);
            return buildLowConfidenceResult("AI returned empty response");
        }

        return parseAiResponse(aiResponse);
    }

    /**
     * 处理 AI 响应文本：提取 JSON、反序列化、校验。
     */
    ParsedConditionsDTO parseAiResponse(String aiResponse) {
        String json = extractJson(aiResponse);
        if (json == null) {
            log.warn("Could not extract JSON from AI response, length={}", aiResponse.length());
            return buildLowConfidenceResult("no valid JSON in response");
        }

        ParsedConditionsDTO result;
        try {
            result = objectMapper.readValue(json, ParsedConditionsDTO.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse extracted JSON: {}", e.getMessage());
            return buildLowConfidenceResult("invalid JSON: " + e.getMessage());
        }

        sanitize(result);
        return result;
    }

    /**
     * 从 AI 响应中提取 JSON 字符串。
     * 优先从 markdown 代码围栏中提取，然后尝试匹配包含 hard_conditions 的 JSON 对象。
     */
    String extractJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        // 1. 尝试从 ```json ... ``` 围栏中提取
        Matcher fenceMatcher = JSON_BLOCK_PATTERN.matcher(text);
        if (fenceMatcher.find()) {
            String candidate = fenceMatcher.group(1).trim();
            if (!candidate.isEmpty()) {
                return candidate;
            }
        }

        // 2. 尝试直接解析整段文本
        String trimmed = text.trim();
        if (trimmed.startsWith("{")) {
            return trimmed;
        }

        // 3. 尝试在文本中找到 JSON 对象
        Matcher objectMatcher = JSON_OBJECT_PATTERN.matcher(text);
        if (objectMatcher.find()) {
            return objectMatcher.group();
        }

        return null;
    }

    /**
     * 清理和校验 ParsedConditionsDTO 字段。
     * - 缺失数组转为空列表
     * - confidence 限制在 [0, 1]
     * - 非法 condition type 安全处理
     */
    public void sanitize(ParsedConditionsDTO dto) {
        // 缺失数组 → 空列表
        if (dto.getHardConditions() == null) {
            dto.setHardConditions(new ArrayList<>());
        }
        if (dto.getSoftPreferences() == null) {
            dto.setSoftPreferences(new ArrayList<>());
        }
        if (dto.getAmbiguousTerms() == null) {
            dto.setAmbiguousTerms(new ArrayList<>());
        }
        if (dto.getRequiresConfirmation() == null) {
            dto.setRequiresConfirmation(new ArrayList<>());
        }
        if (dto.getInferredDefaults() == null) {
            dto.setInferredDefaults(new ArrayList<>());
        }

        // confidence 范围校验
        if (dto.getConfidence() == null) {
            dto.setConfidence(0.0);
        } else {
            dto.setConfidence(Math.max(0.0, Math.min(1.0, dto.getConfidence())));
        }

        // 过滤非法硬条件类型
        dto.getHardConditions().removeIf(hc -> {
            if (hc.getType() == null || !VALID_HARD_TYPES.contains(hc.getType())) {
                log.warn("Removing invalid hard condition type: {}", hc.getType());
                return true;
            }
            return false;
        });

        // 过滤非法硬条件值
        dto.getHardConditions().removeIf(hc -> {
            switch (hc.getType()) {
                case "height", "vehicle_length", "vehicle_width" -> {
                    if (hc.getValue() == null || hc.getValue().isBlank()) {
                        log.warn("Removing {} condition with null/empty value", hc.getType());
                        return true;
                    }
                    try {
                        BigDecimal val = new BigDecimal(hc.getValue());
                        if (val.compareTo(BigDecimal.ZERO) <= 0) {
                            log.warn("Removing {} condition with non-positive value: {}", hc.getType(), hc.getValue());
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        log.warn("Removing {} condition with non-numeric value: {}", hc.getType(), hc.getValue());
                        return true;
                    }
                    return false;
                }
                case "charging" -> {
                    if (hc.getValue() != null && !"true".equalsIgnoreCase(hc.getValue())
                            && !"false".equalsIgnoreCase(hc.getValue())) {
                        log.warn("Removing charging condition with invalid value: {}", hc.getValue());
                        return true;
                    }
                    return false;
                }
                case "spot_type", "qualification", "accessibility" -> {
                    if (hc.getValue() == null || hc.getValue().isBlank()) {
                        log.warn("Removing {} condition with null/empty value", hc.getType());
                        return true;
                    }
                    return false;
                }
                default -> {
                    return false;
                }
            }
        });

        // 过滤非法软偏好类型
        dto.getSoftPreferences().removeIf(sp -> {
            if (sp.getType() == null || !VALID_SOFT_TYPES.contains(sp.getType())) {
                log.warn("Removing invalid soft preference type: {}", sp.getType());
                return true;
            }
            // 权重范围校验
            if (sp.getWeight() != null) {
                sp.setWeight(Math.max(0.0, Math.min(1.0, sp.getWeight())));
            }
            return false;
        });
    }

    /**
     * 构建低置信度空结果（不伪造解析成功）。
     */
    private ParsedConditionsDTO buildLowConfidenceResult(String reason) {
        ParsedConditionsDTO dto = new ParsedConditionsDTO();
        dto.setHardConditions(new ArrayList<>());
        dto.setSoftPreferences(new ArrayList<>());
        dto.setConfidence(0.0);
        dto.setAmbiguousTerms(new ArrayList<>());
        dto.setRequiresConfirmation(new ArrayList<>());
        dto.setInferredDefaults(new ArrayList<>());
        log.info("Built low-confidence result: reason={}", reason);
        return dto;
    }
}
