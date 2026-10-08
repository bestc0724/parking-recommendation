package com.silenker.parkingrecommendation.core.rule;

import com.silenker.parkingrecommendation.core.recommender.WeightedScorer;
import com.silenker.parkingrecommendation.model.dto.ParsedConditionsDTO;
import com.silenker.parkingrecommendation.model.dto.RecommendResultDTO;
import com.silenker.parkingrecommendation.model.entity.ParkingSpot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SoftPreferenceSorter unit tests.
 * No MySQL dependency, no LLM dependency — pure unit tests.
 */
class SoftPreferenceSorterTest {

    private SoftPreferenceSorter sorter;

    @BeforeEach
    void setUp() {
        WeightedScorer scorer = new WeightedScorer();
        sorter = new SoftPreferenceSorter(scorer);
    }

    // ==================== Helper methods ====================

    private ParkingSpot createSpot(String spotId, Integer distElevator,
                                   BigDecimal price, Boolean hasCharging,
                                   String spotType) {
        ParkingSpot spot = new ParkingSpot();
        spot.setSpotId(spotId);
        spot.setStatus("available");
        spot.setDistanceToElevator(distElevator);
        spot.setPricePerHour(price);
        spot.setHasCharging(hasCharging);
        spot.setSpotType(spotType);
        spot.setHeightLimit(new BigDecimal("3.00"));
        return spot;
    }

    private ParsedConditionsDTO createConditionsWithPrefs(List<ParsedConditionsDTO.SoftPreference> prefs) {
        ParsedConditionsDTO conditions = new ParsedConditionsDTO();
        conditions.setHardConditions(new ArrayList<>());
        conditions.setSoftPreferences(prefs != null ? prefs : new ArrayList<>());
        conditions.setConfidence(0.9);
        conditions.setAmbiguousTerms(new ArrayList<>());
        conditions.setRequiresConfirmation(new ArrayList<>());
        conditions.setInferredDefaults(new ArrayList<>());
        return conditions;
    }

    private ParsedConditionsDTO.SoftPreference createPref(String type, double weight) {
        ParsedConditionsDTO.SoftPreference sp = new ParsedConditionsDTO.SoftPreference();
        sp.setType(type);
        sp.setWeight(weight);
        sp.setSource("explicit");
        return sp;
    }

    // ==================== 1. Closer to elevator ranks higher ====================
    @Test
    @DisplayName("1. Closer to elevator -> ranks higher")
    void closerToElevatorRanksHigher() {
        ParkingSpot near = createSpot("NEAR", 5, new BigDecimal("20.00"), false, "standard");
        ParkingSpot far = createSpot("FAR", 50, new BigDecimal("20.00"), false, "standard");
        ParkingSpot mid = createSpot("MID", 25, new BigDecimal("20.00"), false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(Arrays.asList(far, near, mid));

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("distance_to_elevator", 1.0));
        ParsedConditionsDTO conditions = createConditionsWithPrefs(prefs);

        List<RecommendResultDTO> results = sorter.sort(candidates, conditions);

        assertEquals(3, results.size());
        assertEquals("NEAR", results.get(0).getSpotId());
        assertEquals("MID", results.get(1).getSpotId());
        assertEquals("FAR", results.get(2).getSpotId());
    }

    // ==================== 2. Lower price ranks higher ====================
    @Test
    @DisplayName("2. Lower price -> ranks higher")
    void lowerPriceRanksHigher() {
        ParkingSpot cheap = createSpot("CHEAP", 20, new BigDecimal("5.00"), false, "standard");
        ParkingSpot mid = createSpot("MID", 20, new BigDecimal("15.00"), false, "standard");
        ParkingSpot expensive = createSpot("EXP", 20, new BigDecimal("25.00"), false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(Arrays.asList(expensive, cheap, mid));

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("price", 1.0));
        ParsedConditionsDTO conditions = createConditionsWithPrefs(prefs);

        List<RecommendResultDTO> results = sorter.sort(candidates, conditions);

        assertEquals(3, results.size());
        assertEquals("CHEAP", results.get(0).getSpotId());
        assertEquals("MID", results.get(1).getSpotId());
        assertEquals("EXP", results.get(2).getSpotId());
    }

    // ==================== 3. Multiple soft preferences by weight ====================
    @Test
    @DisplayName("3. Multiple soft preferences sorted by weighted score")
    void multiplePreferencesByWeight() {
        // S1: close + expensive
        ParkingSpot s1 = createSpot("S1", 5, new BigDecimal("25.00"), false, "standard");
        // S2: far + cheap
        ParkingSpot s2 = createSpot("S2", 50, new BigDecimal("5.00"), false, "standard");
        // S3: middle
        ParkingSpot s3 = createSpot("S3", 25, new BigDecimal("15.00"), false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(Arrays.asList(s2, s1, s3));

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("distance_to_elevator", 0.7));
        prefs.add(createPref("price", 0.3));
        ParsedConditionsDTO conditions = createConditionsWithPrefs(prefs);

        List<RecommendResultDTO> results = sorter.sort(candidates, conditions);

        assertEquals(3, results.size());
        // S1: 0.7*1.0 + 0.3*0.0 = 0.7
        // S2: 0.7*0.0 + 0.3*1.0 = 0.3
        // S3: 0.7*0.5 + 0.3*0.5 = 0.5
        assertEquals("S1", results.get(0).getSpotId());
        assertEquals("S3", results.get(1).getSpotId());
        assertEquals("S2", results.get(2).getSpotId());
    }

    // ==================== 4. All same distance -> no divide by zero ====================
    @Test
    @DisplayName("4. All same distance -> no divide by zero, all score 1.0")
    void allSameDistanceNoDivByZero() {
        ParkingSpot s1 = createSpot("S1", 20, new BigDecimal("10.00"), false, "standard");
        ParkingSpot s2 = createSpot("S2", 20, new BigDecimal("15.00"), false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(Arrays.asList(s1, s2));

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("distance_to_elevator", 1.0));
        ParsedConditionsDTO conditions = createConditionsWithPrefs(prefs);

        List<RecommendResultDTO> results = sorter.sort(candidates, conditions);

        assertEquals(2, results.size());
        for (RecommendResultDTO r : results) {
            assertFalse(Double.isNaN(r.getScore()), "Score should not be NaN");
            assertFalse(Double.isInfinite(r.getScore()), "Score should not be Infinite");
            assertEquals(1.0, r.getScore(), 0.001, "Same distance -> all should score 1.0");
        }
    }

    // ==================== 5. All same price -> no divide by zero ====================
    @Test
    @DisplayName("5. All same price -> no divide by zero, all score 1.0")
    void allSamePriceNoDivByZero() {
        ParkingSpot s1 = createSpot("S1", 10, new BigDecimal("10.00"), false, "standard");
        ParkingSpot s2 = createSpot("S2", 30, new BigDecimal("10.00"), false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(Arrays.asList(s1, s2));

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("price", 1.0));
        ParsedConditionsDTO conditions = createConditionsWithPrefs(prefs);

        List<RecommendResultDTO> results = sorter.sort(candidates, conditions);

        assertEquals(2, results.size());
        for (RecommendResultDTO r : results) {
            assertFalse(Double.isNaN(r.getScore()), "Score should not be NaN");
            assertEquals(1.0, r.getScore(), 0.001, "Same price -> all should score 1.0");
        }
    }

    // ==================== 6. Null weight ignored ====================
    @Test
    @DisplayName("6. Null weight preference is ignored")
    void nullWeightIgnored() {
        ParkingSpot near = createSpot("NEAR", 5, new BigDecimal("20.00"), false, "standard");
        ParkingSpot far = createSpot("FAR", 50, new BigDecimal("20.00"), false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(Arrays.asList(near, far));

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        ParsedConditionsDTO.SoftPreference nullPref = new ParsedConditionsDTO.SoftPreference();
        nullPref.setType("distance_to_elevator");
        nullPref.setWeight(null);
        nullPref.setSource("explicit");
        prefs.add(nullPref);
        ParsedConditionsDTO conditions = createConditionsWithPrefs(prefs);

        List<RecommendResultDTO> results = sorter.sort(candidates, conditions);

        assertEquals(2, results.size());
        // Null weight pref ignored -> default weights (dist 0.5, price 0.5)
        // Same price -> price 1.0 for both, distance differs
        assertEquals("NEAR", results.get(0).getSpotId());
        assertEquals("FAR", results.get(1).getSpotId());
    }

    // ==================== 7. Negative weight ignored ====================
    @Test
    @DisplayName("7. Negative weight preference is ignored")
    void negativeWeightIgnored() {
        ParkingSpot near = createSpot("NEAR", 5, new BigDecimal("20.00"), false, "standard");
        ParkingSpot far = createSpot("FAR", 50, new BigDecimal("20.00"), false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(Arrays.asList(near, far));

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("distance_to_elevator", -0.8));
        ParsedConditionsDTO conditions = createConditionsWithPrefs(prefs);

        List<RecommendResultDTO> results = sorter.sort(candidates, conditions);

        assertEquals(2, results.size());
        // Negative weight ignored -> default weights
        assertEquals("NEAR", results.get(0).getSpotId());
    }

    // ==================== 8. Unknown preference type ignored ====================
    @Test
    @DisplayName("8. Unknown preference type is ignored")
    void unknownPrefTypeIgnored() {
        ParkingSpot near = createSpot("NEAR", 5, new BigDecimal("20.00"), false, "standard");
        ParkingSpot far = createSpot("FAR", 50, new BigDecimal("20.00"), false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(Arrays.asList(near, far));

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("unknown_xyz_type", 1.0));
        ParsedConditionsDTO conditions = createConditionsWithPrefs(prefs);

        List<RecommendResultDTO> results = sorter.sort(candidates, conditions);

        assertEquals(2, results.size());
        // Unknown type ignored -> default weights
        assertEquals("NEAR", results.get(0).getSpotId());
    }

    // ==================== 9. Stable sort with tie-breaker ====================
    @Test
    @DisplayName("9. Same score -> tie-breaker: distance asc, price asc, spotId asc")
    void stableSortTieBreaker() {
        // All spots have same distance and price -> same score
        // Tie-breaker: distance_to_elevator asc, then price asc, then spotId asc
        ParkingSpot s1 = createSpot("B001", 10, new BigDecimal("10.00"), false, "standard");
        ParkingSpot s2 = createSpot("A001", 10, new BigDecimal("10.00"), false, "standard");
        ParkingSpot s3 = createSpot("C001", 10, new BigDecimal("10.00"), false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(Arrays.asList(s1, s2, s3));

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("distance_to_elevator", 1.0));
        prefs.add(createPref("price", 1.0));
        ParsedConditionsDTO conditions = createConditionsWithPrefs(prefs);

        List<RecommendResultDTO> results = sorter.sort(candidates, conditions);

        assertEquals(3, results.size());
        // Same score, same distance, same price -> spotId asc
        assertEquals("A001", results.get(0).getSpotId());
        assertEquals("B001", results.get(1).getSpotId());
        assertEquals("C001", results.get(2).getSpotId());
    }

    // ==================== 10. More than 3 candidates -> return Top 3 ====================
    @Test
    @DisplayName("10. More than 3 candidates -> return Top 3")
    void moreThanThreeReturnsTop3() {
        List<ParkingSpot> candidates = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            candidates.add(createSpot("S" + i, i * 10, new BigDecimal(String.valueOf(i * 5)), false, "standard"));
        }

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("distance_to_elevator", 1.0));
        ParsedConditionsDTO conditions = createConditionsWithPrefs(prefs);

        List<RecommendResultDTO> results = sorter.sort(candidates, conditions);

        assertEquals(3, results.size(), "Should return exactly 3 results");
        assertEquals("S1", results.get(0).getSpotId());
        assertEquals("S2", results.get(1).getSpotId());
        assertEquals("S3", results.get(2).getSpotId());
    }

    // ==================== 11. Less than 3 candidates -> return all ====================
    @Test
    @DisplayName("11. Less than 3 candidates -> return all")
    void lessThanThreeReturnsAll() {
        ParkingSpot s1 = createSpot("S1", 10, new BigDecimal("10.00"), false, "standard");
        ParkingSpot s2 = createSpot("S2", 20, new BigDecimal("15.00"), false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(Arrays.asList(s1, s2));

        ParsedConditionsDTO conditions = createConditionsWithPrefs(new ArrayList<>());

        List<RecommendResultDTO> results = sorter.sort(candidates, conditions);

        assertEquals(2, results.size(), "Should return all 2 candidates");
    }

    // ==================== 12. Empty list -> empty result ====================
    @Test
    @DisplayName("12. Empty list -> empty result")
    void emptyListReturnsEmpty() {
        List<ParkingSpot> candidates = new ArrayList<>();
        ParsedConditionsDTO conditions = createConditionsWithPrefs(new ArrayList<>());

        List<RecommendResultDTO> results = sorter.sort(candidates, conditions);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    // ==================== 13. Score always in [0, 1] ====================
    @Test
    @DisplayName("13. Score always in [0, 1]")
    void scoreAlwaysInZeroToOne() {
        List<ParkingSpot> candidates = new ArrayList<>();
        candidates.add(createSpot("S1", 0, new BigDecimal("0.00"), true, "standard"));
        candidates.add(createSpot("S2", 100, new BigDecimal("100.00"), false, "compact"));
        candidates.add(createSpot("S3", 50, new BigDecimal("50.00"), null, "charging"));
        candidates.add(createSpot("S4", null, null, null, null));

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("distance_to_elevator", 0.3));
        prefs.add(createPref("price", 0.3));
        prefs.add(createPref("charging", 0.2));
        prefs.add(createPref("spot_type", 0.2));
        ParsedConditionsDTO conditions = createConditionsWithPrefs(prefs);

        List<RecommendResultDTO> results = sorter.sort(candidates, conditions);

        for (RecommendResultDTO r : results) {
            assertTrue(r.getScore() >= 0.0, "Score should be >= 0 for " + r.getSpotId());
            assertTrue(r.getScore() <= 1.0, "Score should be <= 1 for " + r.getSpotId());
            assertFalse(Double.isNaN(r.getScore()), "Score should not be NaN for " + r.getSpotId());
            assertFalse(Double.isInfinite(r.getScore()), "Score should not be Infinite for " + r.getSpotId());
        }
    }

    // ==================== 14. Null elements in candidates skipped ====================
    @Test
    @DisplayName("14. Null elements in candidates -> skipped")
    void nullElementsSkipped() {
        ParkingSpot s1 = createSpot("S1", 5, new BigDecimal("10.00"), false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>();
        candidates.add(null);
        candidates.add(s1);
        candidates.add(null);

        ParsedConditionsDTO conditions = createConditionsWithPrefs(new ArrayList<>());

        List<RecommendResultDTO> results = sorter.sort(candidates, conditions);

        assertEquals(1, results.size());
        assertEquals("S1", results.get(0).getSpotId());
    }

    // ==================== 15. Input list not modified ====================
    @Test
    @DisplayName("15. Input list not modified by sort")
    void inputListNotModified() {
        ParkingSpot s1 = createSpot("S1", 5, new BigDecimal("10.00"), false, "standard");
        ParkingSpot s2 = createSpot("S2", 50, new BigDecimal("25.00"), false, "standard");
        ParkingSpot s3 = createSpot("S3", 25, new BigDecimal("15.00"), false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(Arrays.asList(s1, s2, s3));
        int originalSize = candidates.size();

        ParsedConditionsDTO conditions = createConditionsWithPrefs(new ArrayList<>());

        sorter.sort(candidates, conditions);

        assertEquals(originalSize, candidates.size(), "Input list size should not change");
        assertEquals("S1", candidates.get(0).getSpotId());
        assertEquals("S2", candidates.get(1).getSpotId());
        assertEquals("S3", candidates.get(2).getSpotId());
    }

    // ==================== 16. No IAiService dependency ====================
    @Test
    @DisplayName("16. SoftPreferenceSorter does not depend on IAiService")
    void noAiServiceDependency() {
        // Verify by reflection that SoftPreferenceSorter only depends on WeightedScorer
        Class<?> sorterClass = SoftPreferenceSorter.class;
        java.lang.reflect.Field[] fields = sorterClass.getDeclaredFields();
        for (java.lang.reflect.Field field : fields) {
            Class<?> fieldType = field.getType();
            assertFalse(
                    fieldType.getName().contains("IAiService") ||
                    fieldType.getName().contains("AiService"),
                    "SoftPreferenceSorter should not depend on IAiService, but found: " + fieldType.getName()
            );
        }
    }

    // ==================== 17. No preferences -> default weights ====================
    @Test
    @DisplayName("17. No preferences -> default weights used")
    void noPreferencesUsesDefaultWeights() {
        ParkingSpot nearCheap = createSpot("NC", 5, new BigDecimal("5.00"), false, "standard");
        ParkingSpot farExpensive = createSpot("FE", 50, new BigDecimal("25.00"), false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(Arrays.asList(farExpensive, nearCheap));

        ParsedConditionsDTO conditions = createConditionsWithPrefs(null);

        List<RecommendResultDTO> results = sorter.sort(candidates, conditions);

        assertEquals(2, results.size());
        // Default: dist 0.5 + price 0.5
        // NC: 0.5*1.0 + 0.5*1.0 = 1.0
        // FE: 0.5*0.0 + 0.5*0.0 = 0.0
        assertEquals("NC", results.get(0).getSpotId());
        assertEquals("FE", results.get(1).getSpotId());
    }

    // ==================== 18. Charging preference ====================
    @Test
    @DisplayName("18. Charging preference -> spot with charging scores higher")
    void chargingPreferenceScoresHigher() {
        ParkingSpot withCharging = createSpot("CHG", 20, new BigDecimal("10.00"), true, "standard");
        ParkingSpot noCharging = createSpot("NO_CHG", 20, new BigDecimal("10.00"), false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(Arrays.asList(noCharging, withCharging));

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("charging", 1.0));
        ParsedConditionsDTO conditions = createConditionsWithPrefs(prefs);

        List<RecommendResultDTO> results = sorter.sort(candidates, conditions);

        assertEquals(2, results.size());
        assertEquals("CHG", results.get(0).getSpotId());
        assertEquals("NO_CHG", results.get(1).getSpotId());
    }

    // ==================== 19. Spot type preference ====================
    @Test
    @DisplayName("19. Spot type preference -> defined spot type gets neutral, null gets lower")
    void spotTypePreference() {
        ParkingSpot withType = createSpot("TYPE", 20, new BigDecimal("10.00"), false, "compact");
        ParkingSpot nullType = createSpot("NO_TYPE", 20, new BigDecimal("10.00"), false, null);
        List<ParkingSpot> candidates = new ArrayList<>(Arrays.asList(nullType, withType));

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("spot_type", 1.0));
        ParsedConditionsDTO conditions = createConditionsWithPrefs(prefs);

        List<RecommendResultDTO> results = sorter.sort(candidates, conditions);

        assertEquals(2, results.size());
        // Non-null spotType gets neutral score (0.5), null gets 0.0
        assertEquals("TYPE", results.get(0).getSpotId());
        assertEquals("NO_TYPE", results.get(1).getSpotId());
    }

    // ==================== 20. Null distanceToElevator doesn't break sort ====================
    @Test
    @DisplayName("20. Null distanceToElevator -> uses neutral score, doesn't crash")
    void nullDistanceToElevatorDoesntBreakSort() {
        ParkingSpot withDist = createSpot("WITH", 10, new BigDecimal("10.00"), false, "standard");
        ParkingSpot noDist = createSpot("NO_DIST", null, new BigDecimal("10.00"), false, "standard");
        ParkingSpot farDist = createSpot("FAR", 80, new BigDecimal("10.00"), false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(Arrays.asList(noDist, withDist, farDist));

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("distance_to_elevator", 1.0));
        ParsedConditionsDTO conditions = createConditionsWithPrefs(prefs);

        List<RecommendResultDTO> results = sorter.sort(candidates, conditions);

        assertEquals(3, results.size());
        // WITH(10): normalized=(80-10)/(80-10)=1.0
        // NO_DIST(null): neutral=0.5
        // FAR(80): normalized=0.0
        assertEquals("WITH", results.get(0).getSpotId());
        assertEquals("NO_DIST", results.get(1).getSpotId());
        assertEquals("FAR", results.get(2).getSpotId());

        for (RecommendResultDTO r : results) {
            assertFalse(Double.isNaN(r.getScore()));
            assertTrue(r.getScore() >= 0.0 && r.getScore() <= 1.0);
        }
    }

    // ==================== Additional: RecommendResultDTO fields populated ====================
    @Test
    @DisplayName("RecommendResultDTO fields correctly copied from ParkingSpot")
    void resultDtoFieldsPopulated() {
        ParkingSpot spot = createSpot("S_CHECK", 15, new BigDecimal("12.50"), true, "charging");
        spot.setHeightLimit(new BigDecimal("2.50"));
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));

        ParsedConditionsDTO conditions = createConditionsWithPrefs(new ArrayList<>());

        List<RecommendResultDTO> results = sorter.sort(candidates, conditions);

        assertEquals(1, results.size());
        RecommendResultDTO r = results.get(0);
        assertEquals("S_CHECK", r.getSpotId());
        assertEquals(new BigDecimal("2.50"), r.getHeightLimit());
        assertTrue(r.getHasCharging());
        assertEquals("charging", r.getSpotType());
        assertEquals(15, r.getDistanceToElevator());
        assertEquals(new BigDecimal("12.50"), r.getPricePerHour());
        assertNotNull(r.getScore());
        assertNotNull(r.getScoreDetails());
    }

    // ==================== Additional: 0 candidates ====================
    @Test
    @DisplayName("0 candidates with single candidate -> returns 1")
    void singleCandidateReturnsOne() {
        ParkingSpot s1 = createSpot("ONLY", 10, new BigDecimal("10.00"), false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(s1));

        ParsedConditionsDTO conditions = createConditionsWithPrefs(new ArrayList<>());

        List<RecommendResultDTO> results = sorter.sort(candidates, conditions);

        assertEquals(1, results.size());
        assertEquals("ONLY", results.get(0).getSpotId());
    }

    // ==================== SORT-P2-01: scoreBreakdown in DTO ====================
    @Test
    @DisplayName("SORT-P2-01: DTO scoreBreakdown is non-empty and contains expected keys")
    void dtoScoreBreakdownNonEmpty() {
        ParkingSpot s1 = createSpot("S_BD1", 5, new BigDecimal("10.00"), true, "standard");
        ParkingSpot s2 = createSpot("S_BD2", 50, new BigDecimal("20.00"), false, "compact");
        List<ParkingSpot> candidates = new ArrayList<>(Arrays.asList(s1, s2));

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("distance_to_elevator", 0.5));
        prefs.add(createPref("price", 0.5));
        ParsedConditionsDTO conditions = createConditionsWithPrefs(prefs);

        List<RecommendResultDTO> results = sorter.sort(candidates, conditions);

        assertFalse(results.isEmpty());
        RecommendResultDTO r = results.get(0);
        assertNotNull(r.getScoreBreakdown(), "scoreBreakdown should not be null");
        assertFalse(r.getScoreBreakdown().isEmpty(), "scoreBreakdown should not be empty");
        assertTrue(r.getScoreBreakdown().containsKey("distance_to_elevator"),
                "scoreBreakdown should contain distance_to_elevator");
        assertTrue(r.getScoreBreakdown().containsKey("price"),
                "scoreBreakdown should contain price");

        // Verify breakdown structure
        @SuppressWarnings("unchecked")
        Map<String, Object> distDetail = (Map<String, Object>) r.getScoreBreakdown().get("distance_to_elevator");
        assertNotNull(distDetail);
        assertTrue(distDetail.containsKey("normalizedScore"));
        assertTrue(distDetail.containsKey("rawValue"));
        assertTrue(distDetail.containsKey("min"));
        assertTrue(distDetail.containsKey("max"));
        assertTrue(distDetail.containsKey("weight"));
        assertTrue(distDetail.containsKey("contribution"));

        // Verify scoreDetails still works (backward compatibility)
        assertNotNull(r.getScoreDetails());
        assertFalse(r.getScoreDetails().isEmpty());
    }
}
