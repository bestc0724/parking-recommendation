package com.silenker.parkingrecommendation.core.rule;

import com.silenker.parkingrecommendation.core.recommender.WeightedScorer;
import com.silenker.parkingrecommendation.model.dto.ParsedConditionsDTO;
import com.silenker.parkingrecommendation.model.dto.RecommendResultDTO;
import com.silenker.parkingrecommendation.model.entity.ParkingSpot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Soft preference sorter — deterministic sorting layer.
 * <p>
 * Takes filtered candidates and sorts them by soft preferences using
 * the WeightedScorer. Returns Top N results (default 3).
 * <p>
 * No LLM calls. No hard condition re-evaluation.
 * <p>
 * Stable sort tie-breaker (in priority order):
 * <ol>
 *   <li>score descending</li>
 *   <li>distanceToElevator ascending (null treated as max)</li>
 *   <li>pricePerHour ascending (null treated as max)</li>
 *   <li>spotId lexicographic ascending</li>
 * </ol>
 */
@Slf4j
@Component
public class SoftPreferenceSorter {

    private static final int TOP_N = 3;

    private final WeightedScorer weightedScorer;

    public SoftPreferenceSorter(WeightedScorer weightedScorer) {
        this.weightedScorer = Objects.requireNonNull(weightedScorer, "weightedScorer must not be null");
    }

    /**
     * Sort filtered candidates by soft preferences and return Top N.
     *
     * @param candidates filtered candidates (must not be re-filtered for hard conditions)
     * @param conditions parsed conditions containing soft preferences
     * @return sorted Top N results (default 3), or fewer if fewer candidates
     */
    public List<RecommendResultDTO> sort(List<ParkingSpot> candidates,
                                         ParsedConditionsDTO conditions) {
        if (candidates == null || candidates.isEmpty()) {
            log.debug("SoftPreferenceSorter: no candidates, returning empty");
            return Collections.emptyList();
        }

        // Filter out null elements
        List<ParkingSpot> validCandidates = candidates.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (validCandidates.isEmpty()) {
            log.debug("SoftPreferenceSorter: all candidates are null, returning empty");
            return Collections.emptyList();
        }

        // Extract soft preferences
        List<ParsedConditionsDTO.SoftPreference> prefs = (conditions != null)
                ? conditions.getSoftPreferences()
                : null;

        // Score each candidate
        List<RecommendResultDTO> scored = new ArrayList<>(validCandidates.size());
        for (ParkingSpot spot : validCandidates) {
            WeightedScorer.ScoreResult scoreResult = weightedScorer.score(spot, validCandidates, prefs);
            RecommendResultDTO dto = buildResultDto(spot, scoreResult);
            scored.add(dto);
        }

        // Stable sort with tie-breaker
        scored.sort(buildComparator());

        // Return Top N
        int limit = Math.min(TOP_N, scored.size());
        List<RecommendResultDTO> result = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            result.add(scored.get(i));
        }

        log.info("SoftPreferenceSorter: {} candidates -> returning top {}", validCandidates.size(), result.size());
        return result;
    }

    /**
     * Build RecommendResultDTO from ParkingSpot and ScoreResult.
     */
    private RecommendResultDTO buildResultDto(ParkingSpot spot,
                                               WeightedScorer.ScoreResult scoreResult) {
        RecommendResultDTO dto = new RecommendResultDTO();
        dto.setSpotId(spot.getSpotId());
        dto.setHeightLimit(spot.getHeightLimit());
        dto.setHasCharging(spot.getHasCharging());
        dto.setSpotType(spot.getSpotType());
        dto.setDistanceToElevator(spot.getDistanceToElevator());
        dto.setPricePerHour(spot.getPricePerHour());
        dto.setScore(scoreResult.getScore());
        dto.setScoreDetails(new LinkedHashMap<>(scoreResult.getScoreDetails()));
        // Copy scoreBreakdown as Map<String, Object>
        Map<String, Object> breakdownMap = new LinkedHashMap<>();
        scoreResult.getScoreBreakdown().forEach((key, breakdown) -> {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("normalizedScore", breakdown.getNormalizedScore());
            detail.put("rawValue", breakdown.getRawValue());
            detail.put("min", breakdown.getMin());
            detail.put("max", breakdown.getMax());
            detail.put("weight", breakdown.getWeight());
            detail.put("contribution", breakdown.getContribution());
            breakdownMap.put(key, detail);
        });
        dto.setScoreBreakdown(breakdownMap);
        return dto;
    }

    /**
     * Build the stable sort comparator with tie-breakers.
     * <p>
     * Priority:
     * 1. score descending
     * 2. distanceToElevator ascending (null = Integer.MAX_VALUE)
     * 3. pricePerHour ascending (null = max value)
     * 4. spotId ascending (null = empty string)
     */
    private Comparator<RecommendResultDTO> buildComparator() {
        return Comparator
                .comparingDouble(RecommendResultDTO::getScore).reversed()
                .thenComparingInt(r -> r.getDistanceToElevator() != null
                        ? r.getDistanceToElevator() : Integer.MAX_VALUE)
                .thenComparing(r -> r.getPricePerHour() != null
                        ? r.getPricePerHour()
                        : java.math.BigDecimal.valueOf(Long.MAX_VALUE))
                .thenComparing(r -> r.getSpotId() != null ? r.getSpotId() : "");
    }
}
