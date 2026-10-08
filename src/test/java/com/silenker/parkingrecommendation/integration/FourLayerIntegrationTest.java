package com.silenker.parkingrecommendation.integration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silenker.parkingrecommendation.core.explainer.ReasonGenerator;
import com.silenker.parkingrecommendation.core.nlu.ConditionClassifier;
import com.silenker.parkingrecommendation.core.nlu.NluParser;
import com.silenker.parkingrecommendation.core.recommender.WeightedScorer;
import com.silenker.parkingrecommendation.core.rule.HardConditionFilter;
import com.silenker.parkingrecommendation.core.rule.SoftPreferenceSorter;
import com.silenker.parkingrecommendation.model.dto.ParsedConditionsDTO;
import com.silenker.parkingrecommendation.model.dto.RecommendResultDTO;
import com.silenker.parkingrecommendation.model.entity.ParkingSpot;
import com.silenker.parkingrecommendation.model.entity.User;
import com.silenker.parkingrecommendation.service.IAiService;
import com.silenker.parkingrecommendation.service.impl.ExplainerServiceImpl;
import com.silenker.parkingrecommendation.service.impl.RecommenderServiceImpl;
import com.silenker.parkingrecommendation.service.impl.RuleEngineServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Four-layer integration test (pure Java, no Spring context, no MySQL, no real LLM).
 * <p>
 * Exercises the complete pipeline: NLU parse → hard filter → soft sort → explain,
 * using real core components with fake IAiService.
 */
class FourLayerIntegrationTest {

    private ObjectMapper objectMapper;
    private ConditionClassifier classifier;
    private HardConditionFilter hardConditionFilter;
    private WeightedScorer weightedScorer;
    private SoftPreferenceSorter softPreferenceSorter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        classifier = new ConditionClassifier();
        hardConditionFilter = new HardConditionFilter();
        weightedScorer = new WeightedScorer();
        softPreferenceSorter = new SoftPreferenceSorter(weightedScorer);
    }

    // ==================== Helper methods ====================

    private ParkingSpot createSpot(String spotId, BigDecimal heightLimit,
                                   boolean hasCharging, Integer distElevator,
                                   BigDecimal price) {
        ParkingSpot spot = new ParkingSpot();
        spot.setSpotId(spotId);
        spot.setStatus("available");
        spot.setHeightLimit(heightLimit);
        spot.setHasCharging(hasCharging);
        spot.setSpotType("standard");
        spot.setDistanceToElevator(distElevator);
        spot.setPricePerHour(price);
        return spot;
    }

    private User createUser(String userId) {
        User user = new User();
        user.setUserId(userId);
        user.setUsername("test_" + userId);
        user.setRole("driver");
        return user;
    }

    private ParsedConditionsDTO buildConditions(
            List<ParsedConditionsDTO.HardCondition> hards,
            List<ParsedConditionsDTO.SoftPreference> softs) {
        ParsedConditionsDTO conditions = new ParsedConditionsDTO();
        conditions.setHardConditions(hards != null ? new ArrayList<>(hards) : new ArrayList<>());
        conditions.setSoftPreferences(softs != null ? new ArrayList<>(softs) : new ArrayList<>());
        conditions.setConfidence(1.0);
        conditions.setAmbiguousTerms(new ArrayList<>());
        conditions.setRequiresConfirmation(new ArrayList<>());
        conditions.setInferredDefaults(new ArrayList<>());
        return conditions;
    }

    private ParsedConditionsDTO.HardCondition hardCond(String type, String value) {
        ParsedConditionsDTO.HardCondition hc = new ParsedConditionsDTO.HardCondition();
        hc.setType(type);
        hc.setValue(value);
        hc.setSource("explicit");
        return hc;
    }

    private ParsedConditionsDTO.SoftPreference softPref(String type, double weight) {
        ParsedConditionsDTO.SoftPreference sp = new ParsedConditionsDTO.SoftPreference();
        sp.setType(type);
        sp.setWeight(weight);
        sp.setSource("explicit");
        return sp;
    }

    /**
     * Simulate the full confirmParse pipeline:
     * conditions → filter → sort → explain
     */
    private List<RecommendResultDTO> runFullPipeline(
            List<ParkingSpot> candidates,
            ParsedConditionsDTO conditions,
            User user,
            IAiService explanationAi) {

        // Layer 2: Hard condition filter
        List<ParkingSpot> filtered = new RuleEngineServiceImpl(hardConditionFilter)
                .filter(candidates, conditions, user);

        // Layer 3: Soft preference sort
        List<RecommendResultDTO> recommendations = new RecommenderServiceImpl(softPreferenceSorter)
                .recommend(filtered, conditions);

        // Layer 4: Explanation
        ReasonGenerator reasonGenerator = new ReasonGenerator(explanationAi);
        ExplainerServiceImpl explainerService = new ExplainerServiceImpl(reasonGenerator);
        for (RecommendResultDTO rec : recommendations) {
            String reason = explainerService.generateExplanation(rec, conditions, "test query");
            rec.setReason(reason);
        }

        return recommendations;
    }

    // ==================== T10: Normal full pipeline ====================

    @Test
    @DisplayName("T10: Normal flow - height 2.3m + must charge + prefer elevator proximity")
    void t10_normalFullPipeline() {
        // --- Layer 1: NLU parse (fake) ---
        String nluResponse = "{"
                + "\"hard_conditions\": ["
                + "  {\"type\": \"height\", \"value\": \"2.3\", \"unit\": \"m\", \"source\": \"explicit\"},"
                + "  {\"type\": \"charging\", \"value\": \"true\", \"source\": \"explicit\"}"
                + "],"
                + "\"soft_preferences\": ["
                + "  {\"type\": \"distance_to_elevator\", \"weight\": 0.6, \"source\": \"explicit\"}"
                + "],"
                + "\"confidence\": 0.95,"
                + "\"ambiguous_terms\": [],"
                + "\"requires_confirmation\": [],"
                + "\"inferred_defaults\": []"
                + "}";

        IAiService nluAi = query -> nluResponse;
        NluParser nluParser = new NluParser(nluAi, objectMapper);
        NluServiceImpl nluService = new NluServiceImpl(nluParser, classifier);

        ParsedConditionsDTO parsed = nluService.parse("我车高2.3米，必须充电，最好离电梯近");

        // Verify NLU parsed correctly
        assertNotNull(parsed);
        assertEquals(2, parsed.getHardConditions().size(), "Should have 2 hard conditions (height + charging)");
        assertEquals(1, parsed.getSoftPreferences().size(), "Should have 1 soft preference (distance_to_elevator)");

        // --- Layer 2+3+4: Filter → Sort → Explain ---
        List<ParkingSpot> candidates = List.of(
                createSpot("S001", new BigDecimal("2.50"), true, 10, new BigDecimal("8.00")),
                createSpot("S002", new BigDecimal("2.00"), true, 5, new BigDecimal("6.00")),
                createSpot("S003", new BigDecimal("2.50"), false, 8, new BigDecimal("7.00"))
        );
        User user = createUser("U001");

        // Explanation AI returns valid explanation
        IAiService explanationAi = query -> "推荐S001，限高2.5米满足2.3米需求，有充电桩，距电梯10米较近。基于当前时刻数据。";

        List<RecommendResultDTO> results = runFullPipeline(candidates, parsed, user, explanationAi);

        // Verify: S001 passes (height 2.5>=2.3, has charging)
        //         S002 filtered (height 2.0 < 2.3)
        //         S003 filtered (no charging)
        assertEquals(1, results.size(), "Only S001 should pass filtering");
        assertEquals("S001", results.get(0).getSpotId());

        // Verify explanation
        assertNotNull(results.get(0).getReason());
        assertTrue(results.get(0).getReason().contains("S001"),
                "Explanation should contain spot ID");
        assertTrue(results.get(0).getReason().contains("基于当前时刻数据"),
                "Explanation should contain time disclaimer");
    }

    // ==================== T06: Soft charging preference does NOT trigger hard filter ====================

    @Test
    @DisplayName("T06: Soft charging preference - spots without charging are retained")
    void t06_softChargingPreferenceNoHardFilter() {
        // --- Layer 1: NLU parse (fake) - charging as soft preference ---
        String nluResponse = "{"
                + "\"hard_conditions\": [],"
                + "\"soft_preferences\": ["
                + "  {\"type\": \"charging\", \"weight\": 0.5, \"source\": \"explicit\"}"
                + "],"
                + "\"confidence\": 0.85,"
                + "\"ambiguous_terms\": [],"
                + "\"requires_confirmation\": [],"
                + "\"inferred_defaults\": []"
                + "}";

        IAiService nluAi = query -> nluResponse;
        NluParser nluParser = new NluParser(nluAi, objectMapper);
        NluServiceImpl nluService = new NluServiceImpl(nluParser, classifier);

        ParsedConditionsDTO parsed = nluService.parse("最好能充电吧");

        // Verify NLU classified charging as soft preference
        assertTrue(parsed.getHardConditions().isEmpty(),
                "No hard conditions expected for soft charging preference");
        assertTrue(parsed.getSoftPreferences().stream()
                        .anyMatch(sp -> "charging".equals(sp.getType())),
                "Charging should be a soft preference");

        // --- Layer 2: Filter ---
        List<ParkingSpot> candidates = List.of(
                createSpot("S_CHG", new BigDecimal("3.00"), true, 10, new BigDecimal("8.00")),
                createSpot("S_NO_CHG", new BigDecimal("3.00"), false, 5, new BigDecimal("6.00"))
        );
        User user = createUser("U001");

        RuleEngineServiceImpl ruleEngine = new RuleEngineServiceImpl(hardConditionFilter);
        List<ParkingSpot> filtered = ruleEngine.filter(candidates, parsed, user);

        // Both should pass: no hard charging condition means non-charging spots are kept
        assertEquals(2, filtered.size(),
                "Both spots should pass - soft charging preference must not trigger hard filter");

        // --- Layer 3: Sort ---
        RecommenderServiceImpl recommender = new RecommenderServiceImpl(softPreferenceSorter);
        List<RecommendResultDTO> sorted = recommender.recommend(filtered, parsed);

        assertEquals(2, sorted.size());
        // Spot with charging should score higher on charging dimension
        assertEquals("S_CHG", sorted.get(0).getSpotId(),
                "Charging spot should rank first due to charging soft preference");
    }

    // ==================== AI error → driver confirms correct conditions ====================

    @Test
    @DisplayName("AI output error -> driver corrects at confirmation -> filter uses driver's conditions")
    void aiErrorDriverConfirmsCorrectConditions() {
        // --- Layer 1: NLU parse returns WRONG conditions ---
        // AI mistakenly says height=1.5m (too low), charging=false
        String nluResponse = "{"
                + "\"hard_conditions\": ["
                + "  {\"type\": \"height\", \"value\": \"1.5\", \"unit\": \"m\", \"source\": \"inferred\"}"
                + "],"
                + "\"soft_preferences\": [],"
                + "\"confidence\": 0.3,"
                + "\"ambiguous_terms\": [\"car is tall\"],"
                + "\"requires_confirmation\": [\"height\"],"
                + "\"inferred_defaults\": [\"guessed height 1.5m\"]"
                + "}";

        IAiService nluAi = query -> nluResponse;
        NluParser nluParser = new NluParser(nluAi, objectMapper);
        NluServiceImpl nluService = new NluServiceImpl(nluParser, classifier);

        ParsedConditionsDTO aiParsed = nluService.parse("我车挺高的，得充电");
        // AI parsed with low confidence and wrong values
        assertEquals(0.3, aiParsed.getConfidence(), 0.01);

        // --- Driver confirms with CORRECTED conditions ---
        // This simulates the frontend sending the corrected conditions
        ParsedConditionsDTO driverConfirmed = buildConditions(
                List.of(
                        hardCond("height", "2.3"),
                        hardCond("charging", "true")
                ),
                List.of(
                        softPref("distance_to_elevator", 0.5)
                )
        );

        // --- Layer 2: Filter uses driver-confirmed conditions ---
        List<ParkingSpot> candidates = List.of(
                createSpot("S001", new BigDecimal("2.50"), true, 10, new BigDecimal("8.00")),
                createSpot("S002", new BigDecimal("2.50"), false, 5, new BigDecimal("6.00")),
                createSpot("S003", new BigDecimal("2.00"), true, 8, new BigDecimal("7.00"))
        );
        User user = createUser("U001");

        RuleEngineServiceImpl ruleEngine = new RuleEngineServiceImpl(hardConditionFilter);
        List<ParkingSpot> filtered = ruleEngine.filter(candidates, driverConfirmed, user);

        // S001 passes (2.5>=2.3, has charging)
        // S002 filtered (no charging - hard condition)
        // S003 filtered (2.0 < 2.3 - hard condition)
        assertEquals(1, filtered.size(), "Only S001 should pass with driver-confirmed conditions");
        assertEquals("S001", filtered.get(0).getSpotId());

        // --- Layer 3: Sort ---
        RecommenderServiceImpl recommender = new RecommenderServiceImpl(softPreferenceSorter);
        List<RecommendResultDTO> sorted = recommender.recommend(filtered, driverConfirmed);

        assertEquals(1, sorted.size());
        assertEquals("S001", sorted.get(0).getSpotId());
    }

    // ==================== T03: Vehicle height EQUAL to limit -> should PASS ====================

    @Test
    @DisplayName("T03: Vehicle height exactly equals limit -> passes (>= means equal passes)")
    void t03_heightEqualLimitShouldPass() {
        ParsedConditionsDTO conditions = buildConditions(
                List.of(hardCond("height", "2.10")),
                List.of(softPref("distance_to_elevator", 0.5))
        );

        // Spot with height limit EXACTLY equal to vehicle height
        List<ParkingSpot> candidates = List.of(
                createSpot("S_EQ", new BigDecimal("2.10"), false, 10, new BigDecimal("8.00")),
                createSpot("S_LOW", new BigDecimal("2.00"), false, 5, new BigDecimal("6.00"))
        );
        User user = createUser("U001");

        RuleEngineServiceImpl ruleEngine = new RuleEngineServiceImpl(hardConditionFilter);
        List<ParkingSpot> filtered = ruleEngine.filter(candidates, conditions, user);

        // S_EQ should pass: 2.10 >= 2.10
        // S_LOW should be filtered: 2.00 < 2.10
        assertEquals(1, filtered.size(), "Equal height should pass (>= not >)");
        assertEquals("S_EQ", filtered.get(0).getSpotId());
    }

    // ==================== T04: No spots satisfy all hard conditions ====================

    @Test
    @DisplayName("T04: No spots satisfy all hard conditions -> empty result")
    void t04_noSpotsSatisfyConditions() {
        // Require 3m height + charging, but no spot meets both
        ParsedConditionsDTO conditions = buildConditions(
                List.of(
                        hardCond("height", "3.00"),
                        hardCond("charging", "true")
                ),
                List.of(softPref("distance_to_elevator", 0.5))
        );

        List<ParkingSpot> candidates = List.of(
                createSpot("S001", new BigDecimal("2.50"), true, 10, new BigDecimal("8.00")),  // height too low
                createSpot("S002", new BigDecimal("3.50"), false, 5, new BigDecimal("6.00")),   // no charging
                createSpot("S003", new BigDecimal("2.00"), true, 8, new BigDecimal("7.00"))     // both fail
        );
        User user = createUser("U001");

        RuleEngineServiceImpl ruleEngine = new RuleEngineServiceImpl(hardConditionFilter);
        List<ParkingSpot> filtered = ruleEngine.filter(candidates, conditions, user);

        assertTrue(filtered.isEmpty(), "No spot should pass when none satisfy all hard conditions");

        // Sorting on empty list should return empty
        RecommenderServiceImpl recommender = new RecommenderServiceImpl(softPreferenceSorter);
        List<RecommendResultDTO> sorted = recommender.recommend(filtered, conditions);
        assertTrue(sorted.isEmpty(), "No recommendations when no candidates pass filter");
    }

    // ==================== T08: Spot status changes after recommendation ====================

    @Test
    @DisplayName("T08: Spot becomes occupied between filter and reserve -> second filter removes it")
    void t08_spotStatusChangesAfterRecommendation() {
        ParsedConditionsDTO conditions = buildConditions(
                List.of(hardCond("height", "2.30")),
                List.of(softPref("distance_to_elevator", 0.5))
        );

        // First pass: spot is available
        List<ParkingSpot> candidates = List.of(
                createSpot("S001", new BigDecimal("2.50"), true, 10, new BigDecimal("8.00")),
                createSpot("S002", new BigDecimal("2.50"), false, 5, new BigDecimal("6.00"))
        );
        User user = createUser("U001");

        RuleEngineServiceImpl ruleEngine = new RuleEngineServiceImpl(hardConditionFilter);
        List<ParkingSpot> filtered = ruleEngine.filter(candidates, conditions, user);
        assertEquals(2, filtered.size(), "Both spots should pass initially");

        // Simulate: S001 becomes occupied (admin changes status)
        candidates.get(0).setStatus("occupied");

        // Second pass (re-check before reservation)
        List<ParkingSpot> reFiltered = ruleEngine.filter(candidates, conditions, user);
        assertEquals(1, reFiltered.size(), "Only S002 should pass after S001 becomes occupied");
        assertEquals("S002", reFiltered.get(0).getSpotId());
    }

    // ==================== NLU failure doesn't bypass confirmation ====================

    @Test
    @DisplayName("NLU failure returns low-confidence empty result -> confirmation still works with frontend conditions")
    void nluFailureDoesNotBypassConfirmation() {
        // --- Layer 1: NLU parse returns failure/empty ---
        IAiService nluAi = query -> {
            throw new RuntimeException("LLM API connection refused");
        };
        NluParser nluParser = new NluParser(nluAi, objectMapper);
        NluServiceImpl nluService = new NluServiceImpl(nluParser, classifier);

        ParsedConditionsDTO parsed = nluService.parse("随便找个车位");

        // NLU should return low-confidence empty result
        assertNotNull(parsed);
        assertEquals(0.0, parsed.getConfidence(), 0.01, "NLU failure should give confidence 0.0");
        assertTrue(parsed.getHardConditions().isEmpty(), "NLU failure should give empty hard conditions");

        // --- Simulate: frontend/confirm step provides conditions directly ---
        ParsedConditionsDTO frontendConditions = buildConditions(
                List.of(hardCond("charging", "true")),
                List.of(softPref("price", 0.5))
        );

        // --- Layer 2: Filter still works with frontend conditions ---
        List<ParkingSpot> candidates = List.of(
                createSpot("S_CHG", new BigDecimal("3.00"), true, 10, new BigDecimal("10.00")),
                createSpot("S_NO", new BigDecimal("3.00"), false, 5, new BigDecimal("6.00"))
        );
        User user = createUser("U001");

        RuleEngineServiceImpl ruleEngine = new RuleEngineServiceImpl(hardConditionFilter);
        List<ParkingSpot> filtered = ruleEngine.filter(candidates, frontendConditions, user);

        // Only charging spot passes
        assertEquals(1, filtered.size());
        assertEquals("S_CHG", filtered.get(0).getSpotId());

        // --- Layer 3+4: Sort + Explain still work ---
        RecommenderServiceImpl recommender = new RecommenderServiceImpl(softPreferenceSorter);
        List<RecommendResultDTO> sorted = recommender.recommend(filtered, frontendConditions);

        assertFalse(sorted.isEmpty());

        IAiService explanationAi = query -> "推荐S_CHG，有充电桩满足需求。基于当前时刻数据。";
        ReasonGenerator reasonGenerator = new ReasonGenerator(explanationAi);
        ExplainerServiceImpl explainer = new ExplainerServiceImpl(reasonGenerator);

        for (RecommendResultDTO rec : sorted) {
            String reason = explainer.generateExplanation(rec, frontendConditions, "随便找个车位");
            assertNotNull(reason);
            assertTrue(reason.contains("基于当前时刻数据"));
        }
    }

    // ==================== Explanation API failure uses safe fallback ====================

    @Test
    @DisplayName("Explanation API failure -> safe fallback with '基于当前时刻数据'")
    void explanationApiFailureUsesSafeFallback() {
        // --- Build conditions and candidates directly ---
        ParsedConditionsDTO conditions = buildConditions(
                List.of(
                        hardCond("height", "2.3"),
                        hardCond("charging", "true")
                ),
                List.of(
                        softPref("distance_to_elevator", 0.6)
                )
        );

        List<ParkingSpot> candidates = List.of(
                createSpot("S001", new BigDecimal("2.50"), true, 10, new BigDecimal("8.00"))
        );
        User user = createUser("U001");

        // --- Layer 2: Filter ---
        RuleEngineServiceImpl ruleEngine = new RuleEngineServiceImpl(hardConditionFilter);
        List<ParkingSpot> filtered = ruleEngine.filter(candidates, conditions, user);
        assertEquals(1, filtered.size());

        // --- Layer 3: Sort ---
        RecommenderServiceImpl recommender = new RecommenderServiceImpl(softPreferenceSorter);
        List<RecommendResultDTO> sorted = recommender.recommend(filtered, conditions);
        assertEquals(1, sorted.size());

        // --- Layer 4: Explanation with FAILING AI ---
        IAiService failingAi = query -> {
            throw new RuntimeException("Explanation API timeout");
        };
        ReasonGenerator reasonGenerator = new ReasonGenerator(failingAi);
        ExplainerServiceImpl explainer = new ExplainerServiceImpl(reasonGenerator);

        for (RecommendResultDTO rec : sorted) {
            String reason = explainer.generateExplanation(rec, conditions, "我车高2.3米，必须充电");
            assertNotNull(reason, "Fallback reason must not be null");
            assertTrue(reason.contains("S001"),
                    "Fallback should contain spot ID");
            assertTrue(reason.contains("基于当前时刻数据"),
                    "Fallback must contain time disclaimer");
        }
    }

    // ==================== Inner NluServiceImpl for testing ====================
    // Minimal reimplementation since NluServiceImpl is @Service (requires Spring),
    // and we're testing without Spring context.

    private static class NluServiceImpl implements com.silenker.parkingrecommendation.service.INluService {
        private final NluParser nluParser;
        private final ConditionClassifier classifier;

        NluServiceImpl(NluParser nluParser, ConditionClassifier classifier) {
            this.nluParser = nluParser;
            this.classifier = classifier;
        }

        @Override
        public ParsedConditionsDTO parse(String query) {
            ParsedConditionsDTO result = nluParser.parse(query);
            result = classifier.classify(result, query);
            return result;
        }
    }

    // ==================== CROSS-P1-01: Frontend conditions sanitized ====================

    @Nested
    @DisplayName("CROSS-P1-01: Frontend submitted conditions sanitized")
    class FrontendConditionSanitizeTests {

        @Test
        @DisplayName("Frontend conditions with invalid charging value='required' are sanitized")
        void invalidFrontendChargingValueSanitized() {
            // Simulate frontend submitting charging='required' (not 'true'/'false')
            ParsedConditionsDTO conditions = buildConditions(
                    List.of(hardCond("charging", "required")),
                    List.of(softPref("distance_to_elevator", 0.5))
            );

            // Sanitize via NluParser
            IAiService fakeAi = query -> "{}";
            NluParser nluParser = new NluParser(fakeAi, objectMapper);
            nluParser.sanitize(conditions);

            // charging='required' should be removed by sanitize (not 'true'/'false')
            assertTrue(conditions.getHardConditions().stream()
                            .noneMatch(hc -> "charging".equals(hc.getType())),
                    "charging='required' should be sanitized out");

            // Now run pipeline — non-charging spots should pass
            List<ParkingSpot> candidates = List.of(
                    createSpot("S_NO_CHG", new BigDecimal("3.00"), false, 10, new BigDecimal("8.00")),
                    createSpot("S_CHG", new BigDecimal("3.00"), true, 5, new BigDecimal("6.00"))
            );
            User user = createUser("U001");

            RuleEngineServiceImpl ruleEngine = new RuleEngineServiceImpl(hardConditionFilter);
            List<ParkingSpot> filtered = ruleEngine.filter(candidates, conditions, user);

            assertEquals(2, filtered.size(),
                    "Both spots should pass — charging condition was sanitized out");
        }

        @Test
        @DisplayName("Frontend conditions with non-numeric height are sanitized")
        void invalidFrontendHeightSanitized() {
            ParsedConditionsDTO conditions = buildConditions(
                    List.of(hardCond("height", "abc")),
                    List.of(softPref("distance_to_elevator", 0.5))
            );

            IAiService fakeAi = query -> "{}";
            NluParser nluParser = new NluParser(fakeAi, objectMapper);
            nluParser.sanitize(conditions);

            assertTrue(conditions.getHardConditions().stream()
                            .noneMatch(hc -> "height".equals(hc.getType())),
                    "Non-numeric height should be sanitized out");
        }
    }

    // ==================== CROSS-P1-03: getRecommend null field safety ====================

    @Nested
    @DisplayName("CROSS-P1-03: Recommendation with null fields doesn't NPE")
    class NullFieldSafetyTests {

        @Test
        @DisplayName("RecommendResultDTO with null heightLimit/pricePerHour handled gracefully")
        void nullFieldsHandledGracefully() {
            // Build a recommendation with some null fields
            RecommendResultDTO rec = new RecommendResultDTO();
            rec.setSpotId("S_NULL");
            rec.setHeightLimit(null);  // null
            rec.setHasCharging(false);
            rec.setSpotType("standard");
            rec.setDistanceToElevator(null);  // null
            rec.setPricePerHour(null);  // null
            rec.setScore(0.5);

            // Build minimal conditions
            ParsedConditionsDTO conditions = buildConditions(
                    List.of(),
                    List.of(softPref("distance_to_elevator", 0.5))
            );

            List<ParkingSpot> candidates = List.of(
                    createSpot("S_NULL", null, false, null, null)
            );

            // Sort should not crash with null fields
            RecommenderServiceImpl recommender = new RecommenderServiceImpl(softPreferenceSorter);
            List<RecommendResultDTO> sorted = recommender.recommend(candidates, conditions);

            assertNotNull(sorted);
            assertFalse(sorted.isEmpty());

            // Explain should not crash with null fields
            IAiService fakeAi = query -> "推荐S_NULL。基于当前时刻数据。";
            ReasonGenerator reasonGenerator = new ReasonGenerator(fakeAi);
            ExplainerServiceImpl explainer = new ExplainerServiceImpl(reasonGenerator);

            String reason = explainer.generateExplanation(sorted.get(0), conditions, "test");
            assertNotNull(reason, "Explanation should not be null even with null fields");
        }
    }
}
