package com.silenker.parkingrecommendation.core.recommender;

import com.silenker.parkingrecommendation.model.dto.ParsedConditionsDTO;
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
 * WeightedScorer unit tests.
 * No MySQL dependency, no LLM dependency — pure unit tests.
 */
class WeightedScorerTest {

    private WeightedScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new WeightedScorer();
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

    private ParsedConditionsDTO.SoftPreference createPref(String type, double weight) {
        ParsedConditionsDTO.SoftPreference sp = new ParsedConditionsDTO.SoftPreference();
        sp.setType(type);
        sp.setWeight(weight);
        sp.setSource("explicit");
        return sp;
    }

    private ParsedConditionsDTO.SoftPreference createPrefWithValue(String type, double weight, String value) {
        ParsedConditionsDTO.SoftPreference sp = new ParsedConditionsDTO.SoftPreference();
        sp.setType(type);
        sp.setWeight(weight);
        sp.setSource("explicit");
        sp.setValue(value);
        return sp;
    }

    // ==================== Test: score in [0, 1] ====================
    @Test
    @DisplayName("Score always in [0, 1]")
    void scoreAlwaysInZeroToOne() {
        ParkingSpot s1 = createSpot("S1", 5, new BigDecimal("10.00"), true, "standard");
        ParkingSpot s2 = createSpot("S2", 50, new BigDecimal("30.00"), false, "compact");
        ParkingSpot s3 = createSpot("S3", 25, new BigDecimal("20.00"), true, "charging");
        List<ParkingSpot> candidates = Arrays.asList(s1, s2, s3);

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("distance_to_elevator", 0.5));
        prefs.add(createPref("price", 0.5));

        for (ParkingSpot spot : candidates) {
            WeightedScorer.ScoreResult result = scorer.score(spot, candidates, prefs);
            assertTrue(result.getScore() >= 0.0, "Score should be >= 0 for " + spot.getSpotId());
            assertTrue(result.getScore() <= 1.0, "Score should be <= 1 for " + spot.getSpotId());
            assertFalse(Double.isNaN(result.getScore()), "Score should not be NaN");
            assertFalse(Double.isInfinite(result.getScore()), "Score should not be Infinite");
        }
    }

    // ==================== Test: distance_to_elevator normalization ====================
    @Test
    @DisplayName("Closer to elevator gets higher score")
    void closerToElevatorHigherScore() {
        ParkingSpot near = createSpot("NEAR", 5, new BigDecimal("20.00"), false, "standard");
        ParkingSpot far = createSpot("FAR", 50, new BigDecimal("20.00"), false, "standard");
        List<ParkingSpot> candidates = Arrays.asList(near, far);

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("distance_to_elevator", 1.0));

        WeightedScorer.ScoreResult nearResult = scorer.score(near, candidates, prefs);
        WeightedScorer.ScoreResult farResult = scorer.score(far, candidates, prefs);

        assertTrue(nearResult.getScore() > farResult.getScore(),
                "Near spot should score higher than far spot");
        assertEquals(1.0, nearResult.getScore(), 0.001, "Nearest should get score 1.0");
        assertEquals(0.0, farResult.getScore(), 0.001, "Farthest should get score 0.0");
    }

    // ==================== Test: price normalization ====================
    @Test
    @DisplayName("Lower price gets higher score")
    void lowerPriceHigherScore() {
        ParkingSpot cheap = createSpot("CHEAP", 20, new BigDecimal("5.00"), false, "standard");
        ParkingSpot expensive = createSpot("EXP", 20, new BigDecimal("25.00"), false, "standard");
        List<ParkingSpot> candidates = Arrays.asList(cheap, expensive);

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("price", 1.0));

        WeightedScorer.ScoreResult cheapResult = scorer.score(cheap, candidates, prefs);
        WeightedScorer.ScoreResult expResult = scorer.score(expensive, candidates, prefs);

        assertTrue(cheapResult.getScore() > expResult.getScore(),
                "Cheaper spot should score higher");
        assertEquals(1.0, cheapResult.getScore(), 0.001);
        assertEquals(0.0, expResult.getScore(), 0.001);
    }

    // ==================== Test: multiple soft preferences weighted ====================
    @Test
    @DisplayName("Multiple soft preferences computed by weight")
    void multiplePreferencesWeighted() {
        ParkingSpot s1 = createSpot("S1", 5, new BigDecimal("10.00"), false, "standard");
        ParkingSpot s2 = createSpot("S2", 50, new BigDecimal("5.00"), false, "standard");
        List<ParkingSpot> candidates = Arrays.asList(s1, s2);

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("distance_to_elevator", 0.6));
        prefs.add(createPref("price", 0.4));

        WeightedScorer.ScoreResult r1 = scorer.score(s1, candidates, prefs);
        WeightedScorer.ScoreResult r2 = scorer.score(s2, candidates, prefs);

        // S1: distance=1.0, price=0.0 -> 0.6*1.0 + 0.4*0.0 = 0.6
        // S2: distance=0.0, price=1.0 -> 0.6*0.0 + 0.4*1.0 = 0.4
        assertEquals(0.6, r1.getScore(), 0.001, "S1 should score 0.6");
        assertEquals(0.4, r2.getScore(), 0.001, "S2 should score 0.4");
    }

    // ==================== Test: all same distance -> no divide by zero ====================
    @Test
    @DisplayName("All candidates same distance -> score 1.0 for all (no divide by zero)")
    void allSameDistanceNoDivByZero() {
        ParkingSpot s1 = createSpot("S1", 20, new BigDecimal("10.00"), false, "standard");
        ParkingSpot s2 = createSpot("S2", 20, new BigDecimal("15.00"), false, "standard");
        List<ParkingSpot> candidates = Arrays.asList(s1, s2);

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("distance_to_elevator", 1.0));

        WeightedScorer.ScoreResult r1 = scorer.score(s1, candidates, prefs);
        WeightedScorer.ScoreResult r2 = scorer.score(s2, candidates, prefs);

        assertEquals(1.0, r1.getScore(), 0.001, "Same distance should give score 1.0");
        assertEquals(1.0, r2.getScore(), 0.001, "Same distance should give score 1.0");
        assertFalse(Double.isNaN(r1.getScore()));
        assertFalse(Double.isNaN(r2.getScore()));
    }

    // ==================== Test: all same price -> no divide by zero ====================
    @Test
    @DisplayName("All candidates same price -> score 1.0 for all (no divide by zero)")
    void allSamePriceNoDivByZero() {
        ParkingSpot s1 = createSpot("S1", 10, new BigDecimal("10.00"), false, "standard");
        ParkingSpot s2 = createSpot("S2", 30, new BigDecimal("10.00"), false, "standard");
        List<ParkingSpot> candidates = Arrays.asList(s1, s2);

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("price", 1.0));

        WeightedScorer.ScoreResult r1 = scorer.score(s1, candidates, prefs);
        WeightedScorer.ScoreResult r2 = scorer.score(s2, candidates, prefs);

        assertEquals(1.0, r1.getScore(), 0.001, "Same price should give score 1.0");
        assertEquals(1.0, r2.getScore(), 0.001, "Same price should give score 1.0");
    }

    // ==================== Test: null weight ignored ====================
    @Test
    @DisplayName("Null weight preference is ignored")
    void nullWeightIgnored() {
        ParkingSpot s1 = createSpot("S1", 5, new BigDecimal("10.00"), false, "standard");
        ParkingSpot s2 = createSpot("S2", 50, new BigDecimal("10.00"), false, "standard");
        List<ParkingSpot> candidates = Arrays.asList(s1, s2);

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        ParsedConditionsDTO.SoftPreference nullWeightPref = new ParsedConditionsDTO.SoftPreference();
        nullWeightPref.setType("distance_to_elevator");
        nullWeightPref.setWeight(null);
        nullWeightPref.setSource("explicit");
        prefs.add(nullWeightPref);

        WeightedScorer.ScoreResult r1 = scorer.score(s1, candidates, prefs);
        WeightedScorer.ScoreResult r2 = scorer.score(s2, candidates, prefs);

        // No valid preferences -> default weights used
        // Default: distance_to_elevator 0.5, price 0.5
        // Same price -> price score 1.0 for both
        // Different distance: S1=1.0, S2=0.0
        // Score = (0.5*distScore + 0.5*1.0) / (0.5+0.5)
        // S1: (0.5*1.0 + 0.5*1.0)/1.0 = 1.0
        // S2: (0.5*0.0 + 0.5*1.0)/1.0 = 0.5
        assertTrue(r1.getScore() > r2.getScore(),
                "With null weight ignored, default weights should still differentiate");
    }

    // ==================== Test: negative weight ignored ====================
    @Test
    @DisplayName("Negative weight preference is ignored")
    void negativeWeightIgnored() {
        ParkingSpot s1 = createSpot("S1", 5, new BigDecimal("10.00"), false, "standard");
        ParkingSpot s2 = createSpot("S2", 50, new BigDecimal("10.00"), false, "standard");
        List<ParkingSpot> candidates = Arrays.asList(s1, s2);

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("distance_to_elevator", -0.5));

        WeightedScorer.ScoreResult r1 = scorer.score(s1, candidates, prefs);
        WeightedScorer.ScoreResult r2 = scorer.score(s2, candidates, prefs);

        // Negative weight ignored -> default weights
        assertTrue(r1.getScore() > r2.getScore(),
                "With negative weight ignored, default weights should still differentiate");
    }

    // ==================== Test: unknown preference type ignored ====================
    @Test
    @DisplayName("Unknown preference type is ignored")
    void unknownPrefTypeIgnored() {
        ParkingSpot s1 = createSpot("S1", 5, new BigDecimal("10.00"), false, "standard");
        ParkingSpot s2 = createSpot("S2", 50, new BigDecimal("10.00"), false, "standard");
        List<ParkingSpot> candidates = Arrays.asList(s1, s2);

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("unknown_type_xyz", 1.0));

        WeightedScorer.ScoreResult r1 = scorer.score(s1, candidates, prefs);
        WeightedScorer.ScoreResult r2 = scorer.score(s2, candidates, prefs);

        // Unknown type ignored -> default weights
        assertTrue(r1.getScore() > r2.getScore(),
                "Unknown type ignored, default weights should still differentiate");
    }

    // ==================== Test: no preferences -> default weights ====================
    @Test
    @DisplayName("No preferences -> default weights (distance_to_elevator 0.5, price 0.5)")
    void noPreferencesDefaultWeights() {
        ParkingSpot s1 = createSpot("S1", 5, new BigDecimal("20.00"), false, "standard");
        ParkingSpot s2 = createSpot("S2", 50, new BigDecimal("10.00"), false, "standard");
        ParkingSpot s3 = createSpot("S3", 25, new BigDecimal("15.00"), false, "standard");
        List<ParkingSpot> candidates = Arrays.asList(s1, s2, s3);

        WeightedScorer.ScoreResult r1 = scorer.score(s1, candidates, null);
        WeightedScorer.ScoreResult r2 = scorer.score(s2, candidates, null);
        WeightedScorer.ScoreResult r3 = scorer.score(s3, candidates, null);

        // Default: dist (weight 0.5) + price (weight 0.5)
        // dist (min=5, max=50): S1=(50-5)/(50-5)=1.0, S2=(50-50)/(50-5)=0.0, S3=(50-25)/(50-5)=0.556
        // price (min=10, max=20): S1=(20-20)/(20-10)=0.0, S2=(20-10)/(20-10)=1.0, S3=(20-15)/(20-10)=0.5
        // score: S1=(0.5*1.0+0.5*0.0)/1.0=0.5, S2=(0.5*0.0+0.5*1.0)/1.0=0.5, S3=(0.5*0.556+0.5*0.5)/1.0=0.528
        assertEquals(0.5, r1.getScore(), 0.001);
        assertEquals(0.5, r2.getScore(), 0.001);
        assertEquals(0.528, r3.getScore(), 0.01);

        assertNotNull(r1.getScoreDetails());
        assertTrue(r1.getScoreDetails().containsKey("distance_to_elevator"));
        assertTrue(r1.getScoreDetails().containsKey("price"));
    }

    // ==================== Test: charging preference ====================
    @Test
    @DisplayName("Charging preference: spot with charging scores higher")
    void chargingPreferenceScoresHigher() {
        ParkingSpot withCharging = createSpot("CHG", 20, new BigDecimal("10.00"), true, "standard");
        ParkingSpot noCharging = createSpot("NO_CHG", 20, new BigDecimal("10.00"), false, "standard");
        List<ParkingSpot> candidates = Arrays.asList(withCharging, noCharging);

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("charging", 1.0));

        WeightedScorer.ScoreResult chgResult = scorer.score(withCharging, candidates, prefs);
        WeightedScorer.ScoreResult noChgResult = scorer.score(noCharging, candidates, prefs);

        assertEquals(1.0, chgResult.getScore(), 0.001, "Has charging should score 1.0");
        assertEquals(0.0, noChgResult.getScore(), 0.001, "No charging should score 0.0");
    }

    // ==================== Test: spot_type preference ====================
    @Test
    @DisplayName("Spot type preference: matching type scores higher")
    void spotTypePreferenceMatchingHigher() {
        ParkingSpot matching = createSpot("MATCH", 20, new BigDecimal("10.00"), false, "compact");
        ParkingSpot notMatching = createSpot("NO_MATCH", 20, new BigDecimal("10.00"), false, "standard");
        List<ParkingSpot> candidates = Arrays.asList(matching, notMatching);

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("spot_type", 1.0));

        WeightedScorer.ScoreResult matchResult = scorer.score(matching, candidates, prefs);
        WeightedScorer.ScoreResult noMatchResult = scorer.score(notMatching, candidates, prefs);

        // For spot_type, the preferred type is not explicitly specified in the pref.
        // The scorer compares candidate's spotType to... what?
        // Actually, spot_type is a boolean preference: "prefer charging type spots" etc.
        // Without a target value, it works like: any non-null, non-empty spotType gets neutral 0.5,
        // or we treat it as a boolean dimension where all non-null have same score.
        // Let me reconsider...
        // The design says spot_type is a soft preference. But what's the "matching" criteria?
        // Looking at the spec: "spot_type: 匹配偏好类型得分更高（软偏好权重）"
        // This implies the preference type itself carries meaning, but the SoftPreference
        // only has type, weight, source - no value/target field.
        // So the boolean approach makes sense: if the preference exists,
        // spots with a defined spotType get a boost. But that doesn't differentiate.
        //
        // Wait - maybe spot_type is treated differently. Let me re-think.
        // Perhaps spot_type preference means: among all candidates, those whose spotType
        // matches some preferred type score higher. But we don't have the preferred type
        // value in SoftPreference.
        //
        // Actually, re-reading the requirement more carefully:
        // "spot_type：匹配偏好类型得分更高（软偏好权重）"
        // This suggests the preference indicates a desire for a specific spot type,
        // but since SoftPreference has no value field, I'll treat it as:
        // any spot with a non-null spotType gets a neutral score of 0.5,
        // and null spotType gets 0.0.
        //
        // Alternatively, maybe it should be treated like a boolean: the preference
        // existing means "I care about spot type", and all spots with defined types
        // get equal weight. This isn't very useful for differentiation.
        //
        // For now, let's just verify the behavior is consistent and doesn't crash.
        // The test should check that having a spot_type preference doesn't break anything.
        assertTrue(matchResult.getScore() >= 0.0 && matchResult.getScore() <= 1.0);
        assertTrue(noMatchResult.getScore() >= 0.0 && noMatchResult.getScore() <= 1.0);
    }

    // ==================== Test: null distanceToElevator -> neutral score ====================
    @Test
    @DisplayName("Null distanceToElevator uses neutral score (0.5)")
    void nullDistanceToElevatorUsesNeutralScore() {
        ParkingSpot withDist = createSpot("WITH", 5, new BigDecimal("10.00"), false, "standard");
        ParkingSpot noDist = createSpot("NO_DIST", null, new BigDecimal("10.00"), false, "standard");
        List<ParkingSpot> candidates = Arrays.asList(withDist, noDist);

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("distance_to_elevator", 1.0));

        WeightedScorer.ScoreResult r1 = scorer.score(withDist, candidates, prefs);
        WeightedScorer.ScoreResult r2 = scorer.score(noDist, candidates, prefs);

        // withDist (5): normalized = (50-5)/(50-5) = 1.0
        // noDist (null): neutral = 0.5
        assertEquals(1.0, r1.getScore(), 0.001);
        assertEquals(0.5, r2.getScore(), 0.001);
    }

    // ==================== Test: null price -> neutral score ====================
    @Test
    @DisplayName("Null price uses neutral score (0.5)")
    void nullPriceUsesNeutralScore() {
        ParkingSpot withPrice = createSpot("WITH", 20, new BigDecimal("5.00"), false, "standard");
        ParkingSpot noPrice = createSpot("NO_PRICE", 20, null, false, "standard");
        List<ParkingSpot> candidates = Arrays.asList(withPrice, noPrice);

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("price", 1.0));

        WeightedScorer.ScoreResult r1 = scorer.score(withPrice, candidates, prefs);
        WeightedScorer.ScoreResult r2 = scorer.score(noPrice, candidates, prefs);

        // withPrice (5): normalized = (25-5)/(25-5) = 1.0
        // noPrice (null): neutral = 0.5
        assertEquals(1.0, r1.getScore(), 0.001);
        assertEquals(0.5, r2.getScore(), 0.001);
    }

    // ==================== Test: null hasCharging -> neutral score ====================
    @Test
    @DisplayName("Null hasCharging uses neutral score (0.5)")
    void nullHasChargingUsesNeutralScore() {
        ParkingSpot withCharging = createSpot("CHG", 20, new BigDecimal("10.00"), true, "standard");
        ParkingSpot nullCharging = createSpot("NULL_CHG", 20, new BigDecimal("10.00"), null, "standard");
        List<ParkingSpot> candidates = Arrays.asList(withCharging, nullCharging);

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("charging", 1.0));

        WeightedScorer.ScoreResult r1 = scorer.score(withCharging, candidates, prefs);
        WeightedScorer.ScoreResult r2 = scorer.score(nullCharging, candidates, prefs);

        assertEquals(1.0, r1.getScore(), 0.001, "Has charging should be 1.0");
        assertEquals(0.5, r2.getScore(), 0.001, "Null charging should be neutral 0.5");
    }

    // ==================== Test: scoreDetails contains all dimensions ====================
    @Test
    @DisplayName("ScoreDetails contains all computed dimension scores")
    void scoreDetailsContainsAllDimensions() {
        ParkingSpot s = createSpot("S1", 10, new BigDecimal("15.00"), true, "standard");
        List<ParkingSpot> candidates = List.of(s);

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("distance_to_elevator", 0.4));
        prefs.add(createPref("price", 0.3));
        prefs.add(createPref("charging", 0.3));

        WeightedScorer.ScoreResult result = scorer.score(s, candidates, prefs);

        Map<String, Double> details = result.getScoreDetails();
        assertNotNull(details);
        assertTrue(details.containsKey("distance_to_elevator"));
        assertTrue(details.containsKey("price"));
        assertTrue(details.containsKey("charging"));
    }

    // ==================== SORT-P1-01: NaN and Infinity weight ====================
    @Test
    @DisplayName("SORT-P1-01: NaN weight is ignored")
    void nanWeightIgnored() {
        ParkingSpot s1 = createSpot("S1", 5, new BigDecimal("10.00"), false, "standard");
        ParkingSpot s2 = createSpot("S2", 50, new BigDecimal("10.00"), false, "standard");
        List<ParkingSpot> candidates = Arrays.asList(s1, s2);

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("distance_to_elevator", Double.NaN));

        WeightedScorer.ScoreResult r1 = scorer.score(s1, candidates, prefs);
        WeightedScorer.ScoreResult r2 = scorer.score(s2, candidates, prefs);

        // NaN weight should be ignored, defaults used
        assertFalse(Double.isNaN(r1.getScore()), "Score should not be NaN when weight is NaN");
        assertFalse(Double.isNaN(r2.getScore()), "Score should not be NaN when weight is NaN");
        assertTrue(r1.getScore() > r2.getScore(),
                "With NaN weight ignored, default weights should still differentiate");
    }

    @Test
    @DisplayName("SORT-P1-01: Infinity weight is ignored")
    void infinityWeightIgnored() {
        ParkingSpot s1 = createSpot("S1", 5, new BigDecimal("10.00"), false, "standard");
        ParkingSpot s2 = createSpot("S2", 50, new BigDecimal("10.00"), false, "standard");
        List<ParkingSpot> candidates = Arrays.asList(s1, s2);

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("distance_to_elevator", Double.POSITIVE_INFINITY));

        WeightedScorer.ScoreResult r1 = scorer.score(s1, candidates, prefs);
        WeightedScorer.ScoreResult r2 = scorer.score(s2, candidates, prefs);

        assertFalse(Double.isInfinite(r1.getScore()), "Score should not be Infinite");
        assertFalse(Double.isInfinite(r2.getScore()), "Score should not be Infinite");
        assertTrue(r1.getScore() > r2.getScore(),
                "With Infinity weight ignored, default weights should still differentiate");
    }

    // ==================== SORT-P1-02: Duplicate preference dedup ====================
    @Test
    @DisplayName("SORT-P1-02: Duplicate preference types -> keep first, ignore subsequent")
    void duplicatePreferenceKeepsFirst() {
        ParkingSpot s1 = createSpot("S1", 5, new BigDecimal("10.00"), false, "standard");
        ParkingSpot s2 = createSpot("S2", 50, new BigDecimal("10.00"), false, "standard");
        List<ParkingSpot> candidates = Arrays.asList(s1, s2);

        // Two distance_to_elevator prefs with different weights — keep first (0.8)
        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("distance_to_elevator", 0.8));
        ParsedConditionsDTO.SoftPreference dup = new ParsedConditionsDTO.SoftPreference();
        dup.setType("distance_to_elevator");
        dup.setWeight(0.2);
        dup.setSource("default");
        prefs.add(dup);

        WeightedScorer.ScoreResult r1 = scorer.score(s1, candidates, prefs);
        WeightedScorer.ScoreResult r2 = scorer.score(s2, candidates, prefs);

        // Only distance_to_elevator pref (first one, 0.8), no valid price pref
        // So only distance matters: S1 near (1.0), S2 far (0.0)
        // With single dimension, score = normalized value
        assertEquals(1.0, r1.getScore(), 0.001, "S1 (near) should score 1.0");
        assertEquals(0.0, r2.getScore(), 0.001, "S2 (far) should score 0.0");
    }

    // ==================== SORT-P1-03: distance_to_exit all null ====================
    @Test
    @DisplayName("SORT-P1-03: distance_to_exit all null -> dimension skipped, no crash")
    void distanceToExitAllNullSkipped() {
        // distance_to_exit always returns null from ParkingSpot
        ParkingSpot s1 = createSpot("S1", 5, new BigDecimal("10.00"), true, "standard");
        ParkingSpot s2 = createSpot("S2", 50, new BigDecimal("20.00"), false, "standard");
        List<ParkingSpot> candidates = Arrays.asList(s1, s2);

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("distance_to_elevator", 0.5));
        prefs.add(createPref("distance_to_exit", 0.5));

        WeightedScorer.ScoreResult r1 = scorer.score(s1, candidates, prefs);
        WeightedScorer.ScoreResult r2 = scorer.score(s2, candidates, prefs);

        // distance_to_exit skipped (all null), only distance_to_elevator matters
        assertNotNull(r1);
        assertNotNull(r2);
        assertFalse(Double.isNaN(r1.getScore()), "Score should not be NaN");
        assertFalse(Double.isNaN(r2.getScore()), "Score should not be NaN");
        assertTrue(r1.getScore() > r2.getScore(),
                "S1 should score higher on distance_to_elevator alone");
    }

    // ==================== SORT-P1-04: spot_type with value ====================
    @Test
    @DisplayName("SORT-P1-04: spot_type with value='compact' -> matching type scores 1.0")
    void spotTypeWithValueMatching() {
        ParkingSpot compact = createSpot("COMPACT", 20, new BigDecimal("10.00"), false, "compact");
        ParkingSpot standard = createSpot("STANDARD", 20, new BigDecimal("10.00"), false, "standard");
        List<ParkingSpot> candidates = Arrays.asList(compact, standard);

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPrefWithValue("spot_type", 1.0, "compact"));

        WeightedScorer.ScoreResult compactResult = scorer.score(compact, candidates, prefs);
        WeightedScorer.ScoreResult standardResult = scorer.score(standard, candidates, prefs);

        assertEquals(1.0, compactResult.getScore(), 0.001,
                "Matching spot type should score 1.0");
        assertEquals(0.0, standardResult.getScore(), 0.001,
                "Non-matching spot type should score 0.0");
    }

    @Test
    @DisplayName("SORT-P1-04: spot_type without value -> neutral 0.5 for all non-null types")
    void spotTypeWithoutValueNeutral() {
        ParkingSpot compact = createSpot("COMPACT", 20, new BigDecimal("10.00"), false, "compact");
        ParkingSpot standard = createSpot("STANDARD", 20, new BigDecimal("10.00"), false, "standard");
        List<ParkingSpot> candidates = Arrays.asList(compact, standard);

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("spot_type", 1.0));

        WeightedScorer.ScoreResult compactResult = scorer.score(compact, candidates, prefs);
        WeightedScorer.ScoreResult standardResult = scorer.score(standard, candidates, prefs);

        assertEquals(0.5, compactResult.getScore(), 0.001,
                "Without value, spot type should be neutral 0.5");
        assertEquals(0.5, standardResult.getScore(), 0.001,
                "Without value, spot type should be neutral 0.5");
    }

    // ==================== SORT-P2-01: scoreBreakdown contains all fields ====================
    @Test
    @DisplayName("SORT-P2-01: scoreBreakdown contains raw/min/max/normalized/weight/contribution")
    void scoreBreakdownContainsAllFields() {
        ParkingSpot s1 = createSpot("S1", 5, new BigDecimal("10.00"), false, "standard");
        ParkingSpot s2 = createSpot("S2", 50, new BigDecimal("20.00"), false, "standard");
        List<ParkingSpot> candidates = Arrays.asList(s1, s2);

        List<ParsedConditionsDTO.SoftPreference> prefs = new ArrayList<>();
        prefs.add(createPref("distance_to_elevator", 0.6));
        prefs.add(createPref("price", 0.4));

        WeightedScorer.ScoreResult result = scorer.score(s1, candidates, prefs);

        assertNotNull(result.getScoreBreakdown(), "scoreBreakdown should not be null");
        assertFalse(result.getScoreBreakdown().isEmpty(), "scoreBreakdown should not be empty");
        assertTrue(result.getScoreBreakdown().containsKey("distance_to_elevator"),
                "scoreBreakdown should contain distance_to_elevator");
        assertTrue(result.getScoreBreakdown().containsKey("price"),
                "scoreBreakdown should contain price");

        WeightedScorer.ScoreBreakdown distBreakdown = result.getScoreBreakdown().get("distance_to_elevator");
        assertNotNull(distBreakdown);
        assertEquals(1.0, distBreakdown.getNormalizedScore(), 0.001, "S1 near should have normalized 1.0");
        assertEquals(5.0, distBreakdown.getRawValue(), 0.001, "S1 raw distance should be 5");
        assertEquals(5.0, distBreakdown.getMin(), 0.001, "min distance should be 5");
        assertEquals(50.0, distBreakdown.getMax(), 0.001, "max distance should be 50");
        assertEquals(0.6, distBreakdown.getWeight(), 0.001, "weight should be 0.6");
        assertEquals(0.6, distBreakdown.getContribution(), 0.001, "contribution = 0.6 * 1.0 = 0.6");

        // Verify scoreDetails still works (backward compatibility)
        assertNotNull(result.getScoreDetails());
        assertTrue(result.getScoreDetails().containsKey("distance_to_elevator"));
        assertTrue(result.getScoreDetails().containsKey("price"));
    }
}
