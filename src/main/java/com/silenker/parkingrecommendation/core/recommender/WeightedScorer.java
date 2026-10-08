package com.silenker.parkingrecommendation.core.recommender;

import com.silenker.parkingrecommendation.model.dto.ParsedConditionsDTO;
import com.silenker.parkingrecommendation.model.entity.ParkingSpot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Weighted scorer — computes a deterministic weighted score for a single parking spot
 * based on soft preferences and candidate statistics.
 * <p>
 * Score = Σ(weight_i × normalized_score_i) / Σ(weight_i)
 * <p>
 * No LLM calls. Pure deterministic computation.
 */
@Slf4j
@Component
public class WeightedScorer {

    private static final double NEUTRAL_SCORE = 0.5;
    private static final double DEFAULT_WEIGHT = 0.5;

    private static final String DISTANCE_TO_ELEVATOR = "distance_to_elevator";
    private static final String DISTANCE_TO_EXIT = "distance_to_exit";
    private static final String PRICE = "price";
    private static final String CHARGING = "charging";
    private static final String SPOT_TYPE = "spot_type";

    /**
     * Known numeric preference types that map to ParkingSpot fields.
     */
    private static final Set<String> NUMERIC_PREF_TYPES = Set.of(
            DISTANCE_TO_ELEVATOR, DISTANCE_TO_EXIT, PRICE
    );

    /**
     * Known boolean preference types.
     */
    private static final Set<String> BOOLEAN_PREF_TYPES = Set.of(
            CHARGING, SPOT_TYPE
    );

    /**
     * Compute the weighted score for a single spot against all candidates.
     *
     * @param spot       the spot to score
     * @param candidates all candidates (used to compute min/max for normalization)
     * @param prefs      soft preferences (null or empty → use defaults)
     * @return ScoreResult with score in [0, 1] and per-dimension details
     */
    public ScoreResult score(ParkingSpot spot, List<ParkingSpot> candidates,
                             List<ParsedConditionsDTO.SoftPreference> prefs) {
        Objects.requireNonNull(spot, "spot must not be null");
        Objects.requireNonNull(candidates, "candidates must not be null");

        List<ParsedConditionsDTO.SoftPreference> effectivePrefs = resolveEffectivePrefs(prefs);

        // Precompute min/max for each numeric dimension
        Map<String, double[]> minMaxMap = new HashMap<>();
        for (ParsedConditionsDTO.SoftPreference pref : effectivePrefs) {
            if (NUMERIC_PREF_TYPES.contains(pref.getType())) {
                minMaxMap.put(pref.getType(), computeMinMax(candidates, pref.getType()));
            }
        }

        // Compute weighted score
        double totalWeightedScore = 0.0;
        double totalWeight = 0.0;
        Map<String, Double> scoreDetails = new LinkedHashMap<>();
        Map<String, ScoreBreakdown> scoreBreakdown = new LinkedHashMap<>();

        for (ParsedConditionsDTO.SoftPreference pref : effectivePrefs) {
            String type = pref.getType();
            double weight = pref.getWeight();

            double dimScore;
            double rawValue = 0.0;
            double min = 0.0;
            double max = 0.0;
            if (NUMERIC_PREF_TYPES.contains(type)) {
                double[] minMax = minMaxMap.get(type);
                if (minMax == null) {
                    // All values null for this dimension — skip (remove from effectivePrefs effect)
                    continue;
                }
                min = minMax[0];
                max = minMax[1];
                dimScore = computeNumericNormalized(spot, type, minMax[0], minMax[1]);
                rawValue = extractNumericValue(spot, type) != null ? extractNumericValue(spot, type) : 0.0;
            } else if (BOOLEAN_PREF_TYPES.contains(type)) {
                dimScore = computeBooleanNormalized(spot, type, pref);
                rawValue = dimScore; // For boolean, rawValue is the normalized value
            } else {
                // Unknown type — skip (shouldn't happen with effective prefs, but be safe)
                log.debug("Unknown preference type '{}', skipping", type);
                continue;
            }

            totalWeightedScore += weight * dimScore;
            totalWeight += weight;
            scoreDetails.put(type, dimScore);
            scoreBreakdown.put(type, new ScoreBreakdown(dimScore, rawValue, min, max, weight, weight * dimScore));
        }

        double rawScore = totalWeight > 0 ? totalWeightedScore / totalWeight : 0.0;
        double clampedScore = Math.max(0.0, Math.min(1.0, rawScore));

        return new ScoreResult(clampedScore, scoreDetails, scoreBreakdown);
    }

    /**
     * Resolve effective preferences: filter invalid prefs, dedup, use defaults if none valid.
     */
    private List<ParsedConditionsDTO.SoftPreference> resolveEffectivePrefs(
            List<ParsedConditionsDTO.SoftPreference> prefs) {

        List<ParsedConditionsDTO.SoftPreference> valid = new ArrayList<>();
        Set<String> knownTypes = new HashSet<>();
        knownTypes.addAll(NUMERIC_PREF_TYPES);
        knownTypes.addAll(BOOLEAN_PREF_TYPES);

        Set<String> seenTypes = new HashSet<>();

        if (prefs != null) {
            for (ParsedConditionsDTO.SoftPreference pref : prefs) {
                if (pref == null) continue;
                if (pref.getWeight() == null) continue;
                if (pref.getWeight() < 0) continue;
                if (Double.isNaN(pref.getWeight()) || Double.isInfinite(pref.getWeight())) continue;
                if (pref.getType() == null) continue;
                if (!knownTypes.contains(pref.getType())) continue;
                // SORT-P1-02: skip duplicate types (keep first)
                if (seenTypes.contains(pref.getType())) continue;
                seenTypes.add(pref.getType());
                valid.add(pref);
            }
        }

        if (!valid.isEmpty()) {
            return valid;
        }

        // No valid preferences — use defaults
        List<ParsedConditionsDTO.SoftPreference> defaults = new ArrayList<>();
        ParsedConditionsDTO.SoftPreference distPref = new ParsedConditionsDTO.SoftPreference();
        distPref.setType(DISTANCE_TO_ELEVATOR);
        distPref.setWeight(DEFAULT_WEIGHT);
        distPref.setSource("default");
        defaults.add(distPref);

        ParsedConditionsDTO.SoftPreference pricePref = new ParsedConditionsDTO.SoftPreference();
        pricePref.setType(PRICE);
        pricePref.setWeight(DEFAULT_WEIGHT);
        pricePref.setSource("default");
        defaults.add(pricePref);

        return defaults;
    }

    /**
     * Compute min and max values for a numeric dimension across candidates.
     * Returns [min, max]. Ignores null values.
     * Returns null if all values are null or no candidates (no valid data).
     */
    private double[] computeMinMax(List<ParkingSpot> candidates, String type) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        boolean hasValue = false;

        for (ParkingSpot c : candidates) {
            if (c == null) continue;
            Double val = extractNumericValue(c, type);
            if (val != null) {
                hasValue = true;
                min = Math.min(min, val);
                max = Math.max(max, val);
            }
        }

        if (!hasValue) {
            return null; // No valid values — should skip this dimension
        }
        return new double[]{min, max};
    }

    /**
     * Extract numeric value for a given dimension from a ParkingSpot.
     * Returns null if the value is not available.
     */
    private Double extractNumericValue(ParkingSpot spot, String type) {
        switch (type) {
            case DISTANCE_TO_ELEVATOR:
                return spot.getDistanceToElevator() != null
                        ? spot.getDistanceToElevator().doubleValue() : null;
            case DISTANCE_TO_EXIT:
                // ParkingSpot entity does not have distanceToExit yet.
                // If the field is added in the future, handle it here.
                return null;
            case PRICE:
                return spot.getPricePerHour() != null
                        ? spot.getPricePerHour().doubleValue() : null;
            default:
                return null;
        }
    }

    /**
     * Compute normalized score for a numeric dimension.
     * <p>
     * For distance-like dimensions (distance_to_elevator, distance_to_exit):
     * normalized = (max - value) / (max - min) — closer is better.
     * <p>
     * For price:
     * normalized = (max - value) / (max - min) — cheaper is better.
     * <p>
     * When max == min (all same): returns 1.0 (no divide by zero).
     * When value is null: returns NEUTRAL_SCORE (0.5).
     */
    private double computeNumericNormalized(ParkingSpot spot, String type,
                                            double min, double max) {
        Double value = extractNumericValue(spot, type);
        if (value == null) {
            return NEUTRAL_SCORE;
        }
        if (max > min) {
            return (max - value) / (max - min);
        }
        // max == min: all candidates have the same value -> 1.0
        return 1.0;
    }

    /**
     * Compute normalized score for a boolean dimension.
     * <p>
     * For charging: true → 1.0, false → 0.0, null → 0.5
     * For spot_type: if preference has value, matching spot type → 1.0, non-matching → 0.0;
     *               if no value, non-null spotType → 0.5, null → 0.0
     */
    private double computeBooleanNormalized(ParkingSpot spot, String type,
                                            ParsedConditionsDTO.SoftPreference pref) {
        switch (type) {
            case CHARGING:
                if (spot.getHasCharging() == null) return NEUTRAL_SCORE;
                return spot.getHasCharging() ? 1.0 : 0.0;
            case SPOT_TYPE:
                if (spot.getSpotType() == null) return 0.0;
                // If preference has a specific value, match against it
                if (pref != null && pref.getValue() != null && !pref.getValue().isBlank()) {
                    return pref.getValue().equals(spot.getSpotType()) ? 1.0 : 0.0;
                }
                return NEUTRAL_SCORE;
            default:
                return NEUTRAL_SCORE;
        }
    }

    /**
     * Result of scoring a single spot.
     */
    public static class ScoreResult {
        private final double score;
        private final Map<String, Double> scoreDetails;
        private final Map<String, ScoreBreakdown> scoreBreakdown;

        public ScoreResult(double score, Map<String, Double> scoreDetails,
                           Map<String, ScoreBreakdown> scoreBreakdown) {
            this.score = score;
            this.scoreDetails = scoreDetails != null
                    ? Collections.unmodifiableMap(scoreDetails)
                    : Collections.emptyMap();
            this.scoreBreakdown = scoreBreakdown != null
                    ? Collections.unmodifiableMap(scoreBreakdown)
                    : Collections.emptyMap();
        }

        public double getScore() {
            return score;
        }

        public Map<String, Double> getScoreDetails() {
            return scoreDetails;
        }

        public Map<String, ScoreBreakdown> getScoreBreakdown() {
            return scoreBreakdown;
        }
    }

    /**
     * Detailed breakdown for a single scoring dimension.
     */
    public static class ScoreBreakdown {
        private final double normalizedScore;
        private final double rawValue;
        private final double min;
        private final double max;
        private final double weight;
        private final double contribution;

        public ScoreBreakdown(double normalizedScore, double rawValue,
                              double min, double max, double weight, double contribution) {
            this.normalizedScore = normalizedScore;
            this.rawValue = rawValue;
            this.min = min;
            this.max = max;
            this.weight = weight;
            this.contribution = contribution;
        }

        public double getNormalizedScore() {
            return normalizedScore;
        }

        public double getRawValue() {
            return rawValue;
        }

        public double getMin() {
            return min;
        }

        public double getMax() {
            return max;
        }

        public double getWeight() {
            return weight;
        }

        public double getContribution() {
            return contribution;
        }
    }
}
