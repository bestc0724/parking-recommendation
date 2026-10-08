package com.silenker.parkingrecommendation.core.explainer;

import com.silenker.parkingrecommendation.model.dto.ParsedConditionsDTO;
import com.silenker.parkingrecommendation.model.dto.RecommendResultDTO;
import com.silenker.parkingrecommendation.service.IAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReasonGenerator unit tests.
 * Uses fake IAiService — no MySQL dependency, no real LLM network dependency.
 */
class ReasonGeneratorTest {

    private RecommendResultDTO recommendation;
    private ParsedConditionsDTO conditions;
    private String originalQuery;

    @BeforeEach
    void setUp() {
        recommendation = new RecommendResultDTO();
        recommendation.setSpotId("S001");
        recommendation.setHeightLimit(new BigDecimal("2.50"));
        recommendation.setHasCharging(true);
        recommendation.setSpotType("standard");
        recommendation.setDistanceToElevator(15);
        recommendation.setPricePerHour(new BigDecimal("8.00"));
        recommendation.setScore(0.85);

        Map<String, Double> scoreDetails = new HashMap<>();
        scoreDetails.put("distance_to_elevator", 0.7);
        scoreDetails.put("price", 0.4);
        recommendation.setScoreDetails(scoreDetails);

        conditions = new ParsedConditionsDTO();
        conditions.setHardConditions(new ArrayList<>());
        conditions.setSoftPreferences(new ArrayList<>());
        conditions.setConfidence(0.9);
        conditions.setAmbiguousTerms(new ArrayList<>());
        conditions.setRequiresConfirmation(new ArrayList<>());
        conditions.setInferredDefaults(new ArrayList<>());

        originalQuery = "车高2.3米，得充电";
    }

    // ==================== Helper ====================

    private ReasonGenerator createGenerator(IAiService aiService) {
        return new ReasonGenerator(aiService);
    }

    private ParsedConditionsDTO.HardCondition createHardCondition(String type, String value) {
        ParsedConditionsDTO.HardCondition hc = new ParsedConditionsDTO.HardCondition();
        hc.setType(type);
        hc.setValue(value);
        hc.setSource("explicit");
        return hc;
    }

    // ==================== Test 1: AI normal response ====================
    @Test
    @DisplayName("1. AI normal response -> use AI-generated explanation")
    void aiNormalResponse() {
        String aiResponse = "推荐S001，限高2.5米满足您的2.3米需求，有充电桩。距电梯15米较近。基于当前时刻数据。";
        IAiService fakeAi = query -> aiResponse;

        ReasonGenerator generator = createGenerator(fakeAi);
        String result = generator.generate(recommendation, conditions, originalQuery);

        assertNotNull(result);
        assertTrue(result.contains("S001"), "Should contain spot ID");
        assertTrue(result.contains("基于当前时刻数据"), "Should contain time disclaimer");
    }

    // ==================== Test 2: AI returns null -> fallback ====================
    @Test
    @DisplayName("2. AI returns null -> dynamic fallback")
    void aiReturnsNullFallback() {
        IAiService fakeAi = query -> null;

        ReasonGenerator generator = createGenerator(fakeAi);
        String result = generator.generate(recommendation, conditions, originalQuery);

        assertNotNull(result);
        assertTrue(result.contains("S001"), "Fallback should contain spot ID");
        assertTrue(result.contains("基于当前时刻数据"), "Fallback should contain time disclaimer");
        assertFalse(result.contains("满足限高和充电等硬性要求"),
                "Fallback should NOT use fixed fallback text");
    }

    // ==================== Test 3: AI throws exception -> fallback ====================
    @Test
    @DisplayName("3. AI throws exception -> dynamic fallback")
    void aiThrowsExceptionFallback() {
        IAiService fakeAi = query -> {
            throw new RuntimeException("Connection refused");
        };

        ReasonGenerator generator = createGenerator(fakeAi);
        String result = generator.generate(recommendation, conditions, originalQuery);

        assertNotNull(result);
        assertTrue(result.contains("S001"), "Fallback should contain spot ID");
        assertTrue(result.contains("基于当前时刻数据"), "Fallback should contain time disclaimer");
    }

    // ==================== Test 4: Fallback with matched hard conditions ====================
    @Test
    @DisplayName("4. Fallback describes matched hard conditions correctly")
    void fallbackWithMatchedHardConditions() {
        conditions.getHardConditions().add(createHardCondition("height", "2.3"));
        conditions.getHardConditions().add(createHardCondition("charging", "true"));

        IAiService fakeAi = query -> null;

        ReasonGenerator generator = createGenerator(fakeAi);
        String result = generator.generate(recommendation, conditions, originalQuery);

        assertNotNull(result);
        assertTrue(result.contains("S001"), "Fallback should contain spot ID");
        // Height limit 2.5 >= 2.3 required, so should match
        assertTrue(result.contains("限高") || result.contains("充电桩"),
                "Fallback should describe matched conditions");
    }

    // ==================== Test 5: Fallback with unverifiable conditions ====================
    @Test
    @DisplayName("5. Fallback does not claim unverifiable conditions are satisfied")
    void fallbackDoesNotClaimUnverifiable() {
        conditions.getHardConditions().add(createHardCondition("qualification", "visitor"));
        conditions.getHardConditions().add(createHardCondition("height", "2.3"));

        IAiService fakeAi = query -> null;

        ReasonGenerator generator = createGenerator(fakeAi);
        String result = generator.generate(recommendation, conditions, originalQuery);

        assertNotNull(result);
        assertTrue(result.contains("部分条件无法确认"),
                "Fallback should indicate some conditions cannot be verified");
        assertFalse(result.contains("资格已满足") || result.contains("资格通过"),
                "Fallback should NOT claim qualification is verified");
    }

    // ==================== Test 6: AI response missing time disclaimer -> appended ====================
    @Test
    @DisplayName("6. AI response missing time disclaimer -> appended")
    void aiResponseMissingDisclaimerAppended() {
        String aiResponse = "推荐S001，限高满足，有充电桩。距电梯较近。";
        IAiService fakeAi = query -> aiResponse;

        ReasonGenerator generator = createGenerator(fakeAi);
        String result = generator.generate(recommendation, conditions, originalQuery);

        assertNotNull(result);
        assertTrue(result.contains("基于当前时刻数据"),
                "Time disclaimer should be appended when missing");
    }

    // ==================== Test 7: AI response already has disclaimer -> not duplicated ====================
    @Test
    @DisplayName("7. AI response already has disclaimer -> not duplicated")
    void aiResponseAlreadyHasDisclaimerNotDuplicated() {
        String aiResponse = "推荐S001，限高满足，有充电桩。基于当前时刻数据。";
        IAiService fakeAi = query -> aiResponse;

        ReasonGenerator generator = createGenerator(fakeAi);
        String result = generator.generate(recommendation, conditions, originalQuery);

        assertNotNull(result);
        int count = countOccurrences(result, "基于当前时刻数据");
        assertEquals(1, count, "Time disclaimer should appear exactly once");
    }

    // ==================== Test 8: ambiguousTerms in prompt ====================
    @Test
    @DisplayName("8. ambiguousTerms non-empty -> appears in prompt")
    void ambiguousTermsInPrompt() {
        conditions.setAmbiguousTerms(List.of("挺高的"));
        conditions.getHardConditions().add(createHardCondition("height", "2.3"));

        final String[] capturedPrompt = {null};
        IAiService fakeAi = query -> {
            capturedPrompt[0] = query;
            return "推荐S001，限高满足。基于当前时刻数据。";
        };

        ReasonGenerator generator = createGenerator(fakeAi);
        generator.generate(recommendation, conditions, originalQuery);

        assertNotNull(capturedPrompt[0], "Prompt should have been captured");
        assertTrue(capturedPrompt[0].contains("挺高的"),
                "Prompt should contain ambiguous terms");
        assertTrue(capturedPrompt[0].contains("模糊表述"),
                "Prompt should label ambiguous terms");
    }

    // ==================== Test 9: inferredDefaults in prompt ====================
    @Test
    @DisplayName("9. inferredDefaults non-empty -> appears in prompt")
    void inferredDefaultsInPrompt() {
        conditions.setInferredDefaults(List.of("价格不限"));

        final String[] capturedPrompt = {null};
        IAiService fakeAi = query -> {
            capturedPrompt[0] = query;
            return "推荐S001，当前车位可用。基于当前时刻数据。";
        };

        ReasonGenerator generator = createGenerator(fakeAi);
        generator.generate(recommendation, conditions, originalQuery);

        assertNotNull(capturedPrompt[0], "Prompt should have been captured");
        assertTrue(capturedPrompt[0].contains("价格不限"),
                "Prompt should contain inferred defaults");
        assertTrue(capturedPrompt[0].contains("推断默认"),
                "Prompt should label inferred defaults");
    }

    // ==================== Test 10: originalQuery in prompt ====================
    @Test
    @DisplayName("10. originalQuery is passed into prompt")
    void originalQueryInPrompt() {
        String query = "我车高2.3米，得充电，最好离电梯近点";

        final String[] capturedPrompt = {null};
        IAiService fakeAi = q -> {
            capturedPrompt[0] = q;
            return "推荐S001，满足需求。基于当前时刻数据。";
        };

        ReasonGenerator generator = createGenerator(fakeAi);
        generator.generate(recommendation, conditions, query);

        assertNotNull(capturedPrompt[0], "Prompt should have been captured");
        assertTrue(capturedPrompt[0].contains("我车高2.3米，得充电，最好离电梯近点"),
                "Prompt should contain original query text");
    }

    // ==================== Test 11: Fallback does NOT contain fixed text ====================
    @Test
    @DisplayName("11. Fallback does NOT contain fixed '满足限高和充电等硬性要求' text")
    void fallbackNoFixedText() {
        // Test with no hard conditions
        IAiService fakeAi = query -> null;

        ReasonGenerator generator = createGenerator(fakeAi);
        String result = generator.generate(recommendation, conditions, originalQuery);

        assertFalse(result.contains("满足限高和充电等硬性要求"),
                "Fallback must not use fixed text");
    }

    // ==================== Test 12: AI returns code fence -> stripped ====================
    @Test
    @DisplayName("12. AI returns code fence -> correctly stripped")
    void aiReturnsCodeFenceStripped() {
        String fencedResponse = "```json\n推荐S001，限高满足，有充电桩。基于当前时刻数据。\n```";
        IAiService fakeAi = query -> fencedResponse;

        ReasonGenerator generator = createGenerator(fakeAi);
        String result = generator.generate(recommendation, conditions, originalQuery);

        assertNotNull(result);
        assertFalse(result.contains("```"), "Code fences should be removed");
        assertTrue(result.contains("S001"), "Content should be preserved");
        assertTrue(result.contains("基于当前时刻数据"), "Time disclaimer should remain");
    }

    // ==================== Test 13: unverifiableConditions include qualification & accessibility ===
    @Test
    @DisplayName("13. unverifiableConditions includes qualification and accessibility")
    void unverifiableConditionsIncludeQualificationAndAccessibility() {
        conditions.getHardConditions().add(createHardCondition("qualification", "employee"));
        conditions.getHardConditions().add(createHardCondition("accessibility", "true"));

        IAiService fakeAi = query -> null;

        ReasonGenerator generator = createGenerator(fakeAi);

        // Use buildFacts directly to inspect facts
        ReasonGenerator.ExplanationFacts facts = generator.buildFacts(recommendation, conditions);

        assertNotNull(facts.getUnverifiableConditions());
        assertTrue(facts.getUnverifiableConditions().size() >= 2,
                "Should have at least 2 unverifiable conditions");
        assertTrue(facts.getUnverifiableConditions().stream()
                        .anyMatch(s -> s.contains("资格")),
                "Unverifiable should include qualification");
        assertTrue(facts.getUnverifiableConditions().stream()
                        .anyMatch(s -> s.contains("无障碍")),
                "Unverifiable should include accessibility");
    }

    // ==================== Test 14: scoreDetails passed to prompt ====================
    @Test
    @DisplayName("14. scoreDetails is passed into prompt")
    void scoreDetailsInPrompt() {
        final String[] capturedPrompt = {null};
        IAiService fakeAi = query -> {
            capturedPrompt[0] = query;
            return "推荐S001，满足需求。基于当前时刻数据。";
        };

        ReasonGenerator generator = createGenerator(fakeAi);
        generator.generate(recommendation, conditions, originalQuery);

        assertNotNull(capturedPrompt[0], "Prompt should have been captured");
        assertTrue(capturedPrompt[0].contains("distance_to_elevator"),
                "Prompt should contain score detail keys");
        assertTrue(capturedPrompt[0].contains("price"),
                "Prompt should contain score detail keys");
    }

    // ==================== Additional edge case tests ====================

    @Test
    @DisplayName("Fallback with no hard conditions and no preferences -> '当前车位可用'")
    void fallbackNoConditionsNoPreferences() {
        // Reset recommendation to minimal
        RecommendResultDTO minimal = new RecommendResultDTO();
        minimal.setSpotId("S999");
        minimal.setHeightLimit(new BigDecimal("3.00"));
        minimal.setHasCharging(false);

        // Empty conditions
        ParsedConditionsDTO emptyConditions = new ParsedConditionsDTO();
        emptyConditions.setHardConditions(new ArrayList<>());
        emptyConditions.setSoftPreferences(new ArrayList<>());
        emptyConditions.setAmbiguousTerms(new ArrayList<>());
        emptyConditions.setRequiresConfirmation(new ArrayList<>());
        emptyConditions.setInferredDefaults(new ArrayList<>());

        IAiService fakeAi = query -> null;

        ReasonGenerator generator = createGenerator(fakeAi);
        String result = generator.generate(minimal, emptyConditions, "随便找个车位");

        assertNotNull(result);
        assertTrue(result.contains("S999"));
        assertTrue(result.contains("当前车位可用"),
                "With no condition info, should say spot is available");
        assertTrue(result.contains("基于当前时刻数据"));
    }

    @Test
    @DisplayName("Code fence with text prefix -> correctly stripped")
    void codeFenceWithTextPrefix() {
        String response = "Here is the explanation:\n```text\n推荐S001，满足需求。基于当前时刻数据。\n```\nEnd.";
        IAiService fakeAi = query -> response;

        ReasonGenerator generator = createGenerator(fakeAi);
        String result = generator.generate(recommendation, conditions, originalQuery);

        assertNotNull(result);
        assertFalse(result.contains("```"), "Code fences should be removed");
        assertTrue(result.contains("S001"), "Content should be preserved");
    }

    @Test
    @DisplayName("AI returns blank string -> fallback")
    void aiReturnsBlankFallback() {
        IAiService fakeAi = query -> "   ";

        ReasonGenerator generator = createGenerator(fakeAi);
        String result = generator.generate(recommendation, conditions, originalQuery);

        assertNotNull(result);
        assertTrue(result.contains("S001"), "Blank AI response should trigger fallback");
        assertTrue(result.contains("基于当前时刻数据"));
    }

    @Test
    @DisplayName("Facts: charging not required but spot has charging -> no match recorded for charging")
    void chargingNotRequiredNoMatchRecorded() {
        // No charging hard condition in conditions
        IAiService fakeAi = query -> null;

        ReasonGenerator generator = createGenerator(fakeAi);
        ReasonGenerator.ExplanationFacts facts = generator.buildFacts(recommendation, conditions);

        // matchedHardConditions should not contain charging match since it's not a hard condition
        assertTrue(facts.getMatchedHardConditions().stream()
                        .noneMatch(s -> s.contains("充电桩")),
                "Charging should not be in matched hard conditions when not required as hard condition");
    }

    @Test
    @DisplayName("Height condition with null value on recommendation -> handled gracefully")
    void heightConditionWithNullRecommendationHeight() {
        recommendation.setHeightLimit(null);
        conditions.getHardConditions().add(createHardCondition("height", "2.3"));

        IAiService fakeAi = query -> null;

        ReasonGenerator generator = createGenerator(fakeAi);
        String result = generator.generate(recommendation, conditions, originalQuery);

        assertNotNull(result);
        assertTrue(result.contains("S001"));
        assertTrue(result.contains("基于当前时刻数据"));
    }

    @Test
    @DisplayName("Spot type matches -> recorded as matched")
    void spotTypeMatchRecorded() {
        conditions.getHardConditions().add(createHardCondition("spot_type", "standard"));

        IAiService fakeAi = query -> null;

        ReasonGenerator generator = createGenerator(fakeAi);
        ReasonGenerator.ExplanationFacts facts = generator.buildFacts(recommendation, conditions);

        assertTrue(facts.getMatchedHardConditions().stream()
                        .anyMatch(s -> s.contains("standard") || s.contains("匹配")),
                "Spot type match should be recorded");
    }

    // ==================== Helper: count substring occurrences ====================
    private int countOccurrences(String text, String substring) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(substring, idx)) != -1) {
            count++;
            idx += substring.length();
        }
        return count;
    }

    // ==================== EXPLAIN-P0-01: System prompt separation ====================
    @Test
    @DisplayName("EXPLAIN-P0-01: callLlm uses EXPLAIN_SYSTEM_PROMPT, not NLU prompt")
    void callLlmUsesExplainSystemPrompt() {
        final String[] capturedSystemPrompt = {null};
        IAiService fakeAi = new IAiService() {
            @Override
            public String chat(String userMessage) {
                return "should not be called";
            }

            @Override
            public String chat(String systemPrompt, String userMessage) {
                capturedSystemPrompt[0] = systemPrompt;
                return "推荐S001，满足需求。基于当前时刻数据。";
            }
        };

        ReasonGenerator generator = createGenerator(fakeAi);
        generator.generate(recommendation, conditions, originalQuery);

        assertNotNull(capturedSystemPrompt[0], "System prompt should have been captured");
        assertTrue(capturedSystemPrompt[0].contains("停车推荐解释助手"),
                "System prompt should be the explanation-specific prompt, not NLU prompt");
        assertFalse(capturedSystemPrompt[0].contains("Return ONLY valid JSON"),
                "System prompt should NOT contain NLU prompt text");
    }

    // ==================== EXPLAIN-P1-01: null parameter safety ====================
    @Nested
    @DisplayName("EXPLAIN-P1-01: null parameter safety")
    class NullParameterSafetyTests {

        @Test
        @DisplayName("null recommendation -> safe fallback string")
        void nullRecommendationReturnsFallback() {
            IAiService fakeAi = query -> null;
            ReasonGenerator generator = createGenerator(fakeAi);

            String result = generator.generate(null, conditions, originalQuery);

            assertNotNull(result);
            assertTrue(result.contains("推荐信息不可用"),
                    "null recommendation should return safe fallback");
            assertTrue(result.contains("基于当前时刻数据"),
                    "Fallback should contain time disclaimer");
        }

        @Test
        @DisplayName("null conditions -> safe fallback string")
        void nullConditionsReturnsFallback() {
            IAiService fakeAi = query -> null;
            ReasonGenerator generator = createGenerator(fakeAi);

            String result = generator.generate(recommendation, null, originalQuery);

            assertNotNull(result);
            assertTrue(result.contains("推荐信息不可用"),
                    "null conditions should return safe fallback");
            assertTrue(result.contains("基于当前时刻数据"),
                    "Fallback should contain time disclaimer");
        }
    }

    // ==================== EXPLAIN-P1-02: not satisfied conditions ====================
    @Nested
    @DisplayName("EXPLAIN-P1-02: not satisfied conditions separation")
    class NotSatisfiedConditionsTests {

        @Test
        @DisplayName("Charging not satisfied -> in notSatisfiedConditions, not matchedHardConditions")
        void chargingNotSatisfiedSeparated() {
            recommendation.setHasCharging(false);
            conditions.getHardConditions().add(createHardCondition("charging", "true"));

            IAiService fakeAi = query -> null;
            ReasonGenerator generator = createGenerator(fakeAi);
            ReasonGenerator.ExplanationFacts facts = generator.buildFacts(recommendation, conditions);

            assertTrue(facts.getNotSatisfiedConditions().stream()
                            .anyMatch(s -> s.contains("充电桩")),
                    "Charging not satisfied should be in notSatisfiedConditions");
            assertTrue(facts.getMatchedHardConditions().stream()
                            .noneMatch(s -> s.contains("充电桩")),
                    "Charging not satisfied should NOT be in matchedHardConditions");
        }

        @Test
        @DisplayName("Charging satisfied -> in matchedHardConditions")
        void chargingSatisfiedInMatched() {
            recommendation.setHasCharging(true);
            conditions.getHardConditions().add(createHardCondition("charging", "true"));

            IAiService fakeAi = query -> null;
            ReasonGenerator generator = createGenerator(fakeAi);
            ReasonGenerator.ExplanationFacts facts = generator.buildFacts(recommendation, conditions);

            assertTrue(facts.getMatchedHardConditions().stream()
                            .anyMatch(s -> s.contains("充电桩")),
                    "Charging satisfied should be in matchedHardConditions");
            assertTrue(facts.getNotSatisfiedConditions().isEmpty(),
                    "Not satisfied should be empty when charging is satisfied");
        }

        @Test
        @DisplayName("Fallback with not-satisfied conditions includes them in output")
        void fallbackWithNotSatisfiedConditions() {
            recommendation.setHasCharging(false);
            conditions.getHardConditions().add(createHardCondition("charging", "true"));

            IAiService fakeAi = query -> null;
            ReasonGenerator generator = createGenerator(fakeAi);
            String result = generator.generate(recommendation, conditions, originalQuery);

            assertNotNull(result);
            assertTrue(result.contains("充电桩") || result.contains("不满足"),
                    "Fallback should mention charging not satisfied");
        }
    }
}
