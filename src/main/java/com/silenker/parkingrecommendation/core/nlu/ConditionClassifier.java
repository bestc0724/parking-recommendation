package com.silenker.parkingrecommendation.core.nlu;

import com.silenker.parkingrecommendation.model.dto.ParsedConditionsDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * NLU 后处理分类器：检查 NLU 输出的硬条件/软偏好分类是否合理。
 * 低于置信度阈值的项加入 requires_confirmation。
 */
@Slf4j
@Component
public class ConditionClassifier {

    /**
     * 置信度阈值：低于此值的解析结果需要人工确认。
     */
    public static final double CONFIDENCE_THRESHOLD = 0.75;

    /**
     * 对 NLU 解析结果进行后处理分类校验。
     * <p>
     * 规则：
     * 1. 整体 confidence < CONFIDENCE_THRESHOLD 时，将所有硬条件加入 requires_confirmation
     * 2. 检查充电分类：必须充电 → 硬条件；模糊充电 → 软偏好
     * 3. 检查车高模糊表达：不允许猜测数字
     * 4. "随便"等不自动补充硬条件
     * 5. 访客/员工 → 硬条件 qualification
     * 6. 无障碍 → 硬条件 accessibility
     *
     * @param dto    原始解析结果（会被修改）
     * @param query  原始用户查询（用于检查模糊表达）
     * @return 后处理后的解析结果
     */
    public ParsedConditionsDTO classify(ParsedConditionsDTO dto, String query) {
        if (dto == null) {
            return dto;
        }

        // 1. 低置信度时，所有硬条件需要确认
        if (dto.getConfidence() != null && dto.getConfidence() < CONFIDENCE_THRESHOLD) {
            log.info("Confidence {} below threshold {}, adding hard conditions to requires_confirmation",
                    dto.getConfidence(), CONFIDENCE_THRESHOLD);
            ensureRequiresConfirmation(dto);
            for (ParsedConditionsDTO.HardCondition hc : dto.getHardConditions()) {
                if (!dto.getRequiresConfirmation().contains(hc.getType())) {
                    dto.getRequiresConfirmation().add(hc.getType());
                }
            }
        }

        // 2. 充电分类校验
        validateChargingClassification(dto, query);

        // 3. 车高模糊表达校验
        validateHeightAmbiguity(dto, query);

        // 4. "随便"类表达不自动补充硬条件
        if (isCasualQuery(query)) {
            validateCasualQuery(dto);
        }

        // 5. 资格条件校验（访客/员工必须是硬条件）
        validateQualification(dto, query);

        // 6. 无障碍条件校验
        validateAccessibility(dto, query);

        return dto;
    }

    /**
     * 校验充电分类：
     * - "必须充电"/"得充电" → 硬条件 charging
     * - "最好能充电"/"有充电更方便" → 软偏好 charging
     * 如果 AI 分类错误，进行修正。
     */
    private void validateChargingClassification(ParsedConditionsDTO dto, String query) {
        if (query == null) return;

        boolean strongCharging = containsStrongChargingExpression(query);
        boolean weakCharging = containsWeakChargingExpression(query);

        if (strongCharging) {
            // 检查是否被误分为软偏好
            boolean inSoft = dto.getSoftPreferences().stream()
                    .anyMatch(sp -> "charging".equals(sp.getType()));
            boolean inHard = dto.getHardConditions().stream()
                    .anyMatch(hc -> "charging".equals(hc.getType()));

            if (inSoft && !inHard) {
                log.warn("Strong charging expression detected but classified as soft preference. Correcting to hard condition.");
                dto.getSoftPreferences().removeIf(sp -> "charging".equals(sp.getType()));
                ParsedConditionsDTO.HardCondition hc = new ParsedConditionsDTO.HardCondition();
                hc.setType("charging");
                hc.setValue("true");
                hc.setSource("corrected");
                dto.getHardConditions().add(hc);
                applyCorrectionPenalty(dto, "charging");
            }
        } else if (weakCharging) {
            // 检查是否被误分为硬条件
            boolean inHard = dto.getHardConditions().stream()
                    .anyMatch(hc -> "charging".equals(hc.getType()));
            boolean inSoft = dto.getSoftPreferences().stream()
                    .anyMatch(sp -> "charging".equals(sp.getType()));

            if (inHard && !inSoft) {
                log.warn("Weak charging expression detected but classified as hard condition. Correcting to soft preference.");
                dto.getHardConditions().removeIf(hc -> "charging".equals(hc.getType()));
                ParsedConditionsDTO.SoftPreference sp = new ParsedConditionsDTO.SoftPreference();
                sp.setType("charging");
                sp.setWeight(0.5);
                sp.setSource("corrected");
                dto.getSoftPreferences().add(sp);
                applyCorrectionPenalty(dto, "charging");
            }
        }
    }

    /**
     * 校验车高模糊表达：
     * "我车挺高的" → 不猜测数字，加入 ambiguous_terms 和 requires_confirmation
     */
    private void validateHeightAmbiguity(ParsedConditionsDTO dto, String query) {
        if (query == null) return;

        String q = query.toLowerCase();
        boolean hasVagueHeight = q.contains("挺高") || q.contains("比较高")
                || q.contains("quite tall") || q.contains("really tall")
                || q.contains("very tall") || q.contains("pretty tall")
                || q.contains("tall car") || q.contains("higher vehicle");

        if (hasVagueHeight) {
            // 移除带数字的硬条件 height（AI 不应猜测）
            boolean removedGuess = dto.getHardConditions().removeIf(hc -> {
                if ("height".equals(hc.getType()) && hc.getValue() != null) {
                    try {
                        Double.parseDouble(hc.getValue());
                        log.warn("Removing guessed height value '{}' for vague expression", hc.getValue());
                        return true;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
                return false;
            });

            if (removedGuess || !containsAmbiguousTerm(dto, "车高模糊")) {
                if (!dto.getAmbiguousTerms().contains("车高模糊")) {
                    dto.getAmbiguousTerms().add("车高模糊");
                }
                if (!dto.getRequiresConfirmation().contains("height")) {
                    dto.getRequiresConfirmation().add("height");
                }
            }
        }
    }

    /**
     * "随便找个车位"等表达 → 不自动补充硬条件。
     * 如果 AI 自行添加了不合理的硬条件，移除。
     */
    private void validateCasualQuery(ParsedConditionsDTO dto) {
        // 移除 inferred source 的硬条件（不应该是随便查询时 AI 自行添加的）
        List<ParsedConditionsDTO.HardCondition> toRemove = new ArrayList<>();
        for (ParsedConditionsDTO.HardCondition hc : dto.getHardConditions()) {
            if ("inferred".equals(hc.getSource())) {
                log.warn("Removing inferred hard condition '{}' from casual query", hc.getType());
                toRemove.add(hc);
            }
        }
        dto.getHardConditions().removeAll(toRemove);
    }

    /**
     * 校验资格条件：访客/员工必须是硬条件。
     */
    private void validateQualification(ParsedConditionsDTO dto, String query) {
        if (query == null) return;

        String q = query.toLowerCase();
        boolean hasQualification = q.contains("访客") || q.contains("员工")
                || q.contains("内部人员") || q.contains("会员")
                || q.contains("visitor") || q.contains("employee")
                || q.contains("member") || q.contains("staff");

        if (hasQualification) {
            boolean inHard = dto.getHardConditions().stream()
                    .anyMatch(hc -> "qualification".equals(hc.getType()));
            if (!inHard) {
                log.warn("Qualification expression detected but not in hard conditions. Adding.");
                ParsedConditionsDTO.HardCondition hc = new ParsedConditionsDTO.HardCondition();
                hc.setType("qualification");
                if (q.contains("访客") || q.contains("visitor")) {
                    hc.setValue("visitor");
                } else if (q.contains("员工") || q.contains("内部人员") || q.contains("employee") || q.contains("staff")) {
                    hc.setValue("employee");
                } else if (q.contains("会员") || q.contains("member")) {
                    hc.setValue("member");
                }
                hc.setSource("corrected");
                dto.getHardConditions().add(hc);
                applyCorrectionPenalty(dto, "qualification");
            }
        }
    }

    /**
     * 校验无障碍条件：必须是硬条件。
     */
    private void validateAccessibility(ParsedConditionsDTO dto, String query) {
        if (query == null) return;

        String q = query.toLowerCase();
        boolean needsAccessible = q.contains("无障碍") || q.contains("残疾")
                || q.contains("轮椅")
                || q.contains("accessible") || q.contains("disability")
                || q.contains("wheelchair");

        if (needsAccessible) {
            boolean inHard = dto.getHardConditions().stream()
                    .anyMatch(hc -> "accessibility".equals(hc.getType()));
            if (!inHard) {
                log.warn("Accessibility expression detected but not in hard conditions. Adding.");
                ParsedConditionsDTO.HardCondition hc = new ParsedConditionsDTO.HardCondition();
                hc.setType("accessibility");
                hc.setValue("true");
                hc.setSource("corrected");
                dto.getHardConditions().add(hc);
                applyCorrectionPenalty(dto, "accessibility");
            }
        }
    }

    /**
     * Apply correction penalty: reduce confidence below threshold and add to requiresConfirmation.
     */
    private void applyCorrectionPenalty(ParsedConditionsDTO dto, String conditionType) {
        ensureRequiresConfirmation(dto);
        dto.setConfidence(CONFIDENCE_THRESHOLD - 0.01);
        if (!dto.getRequiresConfirmation().contains(conditionType)) {
            dto.getRequiresConfirmation().add(conditionType);
        }
    }

    // ---------- helper methods (Chinese + English) ----------

    private boolean containsStrongChargingExpression(String query) {
        String q = query.toLowerCase();
        // Check each strong keyword; if a negation prefix appears before it, exclude
        String[] zhKeywords = {"必须充电", "得充电", "要充电", "需要充电", "一定要充", "不能不充"};
        for (String kw : zhKeywords) {
            int idx = q.indexOf(kw);
            if (idx >= 0 && hasChineseNegationPrefix(q, idx)) {
                continue; // negated — not a strong charging expression
            }
            if (idx >= 0) {
                return true;
            }
        }
        String[] enKeywords = {"must charge", "need to charge", "need charging",
                "require charging", "have to charge", "charging required"};
        for (String kw : enKeywords) {
            int idx = q.indexOf(kw);
            if (idx >= 0 && hasEnglishNegationPrefix(q, idx)) {
                continue; // negated — not a strong charging expression
            }
            if (idx >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a Chinese negation character (不/没/别/未/无/莫) appears right before the keyword.
     */
    private boolean hasChineseNegationPrefix(String query, int keywordIndex) {
        if (keywordIndex <= 0) return false;
        char prev = query.charAt(keywordIndex - 1);
        return prev == '不' || prev == '没' || prev == '别' || prev == '未' || prev == '无' || prev == '莫';
    }

    /**
     * Check if an English negation word (not/don't/do not/no) appears before the keyword.
     */
    private boolean hasEnglishNegationPrefix(String query, int keywordIndex) {
        // Check up to 10 characters before the keyword for negation words
        int start = Math.max(0, keywordIndex - 10);
        String prefix = query.substring(start, keywordIndex).trim();
        return prefix.endsWith("not") || prefix.endsWith("don't")
                || prefix.endsWith("do not") || prefix.endsWith("no");
    }

    private boolean containsWeakChargingExpression(String query) {
        String q = query.toLowerCase();
        return q.contains("最好") && q.contains("充电")
                || q.contains("能充电") && (q.contains("更好") || q.contains("也行"))
                || q.contains("有充电") && q.contains("方便")
                || q.contains("充电吧") && q.contains("不行就算")
                || q.contains("prefer charging") || q.contains("would be nice")
                && q.contains("charg")
                || q.contains("if possible") && q.contains("charg")
                || q.contains("charging if possible");
    }

    private boolean isCasualQuery(String query) {
        String q = query.toLowerCase();
        return q.contains("随便") || q.contains("无所谓")
                || q.contains("都可以") || q.contains("都行")
                || q.contains("停哪都行")
                || q.contains("any spot") || q.contains("just find")
                || q.contains("don't care") || q.contains("whatever")
                || q.contains("park anywhere") || q.contains("just park");
    }

    private boolean containsAmbiguousTerm(ParsedConditionsDTO dto, String term) {
        return dto.getAmbiguousTerms() != null && dto.getAmbiguousTerms().contains(term);
    }

    private void ensureRequiresConfirmation(ParsedConditionsDTO dto) {
        if (dto.getRequiresConfirmation() == null) {
            dto.setRequiresConfirmation(new ArrayList<>());
        }
    }
}
