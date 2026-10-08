package com.silenker.parkingrecommendation.core.rule;

import com.silenker.parkingrecommendation.model.dto.ParsedConditionsDTO;
import com.silenker.parkingrecommendation.model.entity.ParkingSpot;
import com.silenker.parkingrecommendation.model.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HardConditionFilter unit tests.
 * No MySQL dependency, no real LLM network dependency.
 */
class HardConditionFilterTest {

    private HardConditionFilter filter;

    private static final BigDecimal HEIGHT_2_10 = new BigDecimal("2.10");
    private static final BigDecimal HEIGHT_2_11 = new BigDecimal("2.11");
    private static final BigDecimal HEIGHT_2_30 = new BigDecimal("2.30");
    private static final BigDecimal PRICE_8 = new BigDecimal("8.00");
    private static final BigDecimal PRICE_12 = new BigDecimal("12.00");

    @BeforeEach
    void setUp() {
        filter = new HardConditionFilter();
    }

    // ==================== Helper methods ====================

    private ParkingSpot createSpot(String spotId, String status, BigDecimal heightLimit,
                                   boolean hasCharging, String spotType) {
        ParkingSpot spot = new ParkingSpot();
        spot.setSpotId(spotId);
        spot.setStatus(status);
        spot.setHeightLimit(heightLimit);
        spot.setHasCharging(hasCharging);
        spot.setSpotType(spotType);
        spot.setPricePerHour(PRICE_8);
        spot.setDistanceToElevator(10);
        return spot;
    }

    private ParsedConditionsDTO createConditionsWithHeight(BigDecimal height) {
        ParsedConditionsDTO conditions = new ParsedConditionsDTO();
        conditions.setHardConditions(new ArrayList<>());
        ParsedConditionsDTO.HardCondition hc = new ParsedConditionsDTO.HardCondition();
        hc.setType("height");
        hc.setValue(height.toPlainString());
        hc.setUnit("m");
        hc.setSource("explicit");
        conditions.getHardConditions().add(hc);
        conditions.setSoftPreferences(new ArrayList<>());
        conditions.setConfidence(0.9);
        conditions.setAmbiguousTerms(new ArrayList<>());
        conditions.setRequiresConfirmation(new ArrayList<>());
        conditions.setInferredDefaults(new ArrayList<>());
        return conditions;
    }

    private ParsedConditionsDTO createEmptyConditions() {
        ParsedConditionsDTO conditions = new ParsedConditionsDTO();
        conditions.setHardConditions(new ArrayList<>());
        conditions.setSoftPreferences(new ArrayList<>());
        conditions.setConfidence(0.9);
        conditions.setAmbiguousTerms(new ArrayList<>());
        conditions.setRequiresConfirmation(new ArrayList<>());
        conditions.setInferredDefaults(new ArrayList<>());
        return conditions;
    }

    private ParsedConditionsDTO createConditionsChargingRequired() {
        ParsedConditionsDTO conditions = new ParsedConditionsDTO();
        conditions.setHardConditions(new ArrayList<>());
        ParsedConditionsDTO.HardCondition hc = new ParsedConditionsDTO.HardCondition();
        hc.setType("charging");
        hc.setValue("true");
        hc.setSource("explicit");
        conditions.getHardConditions().add(hc);
        conditions.setSoftPreferences(new ArrayList<>());
        conditions.setConfidence(0.9);
        conditions.setAmbiguousTerms(new ArrayList<>());
        conditions.setRequiresConfirmation(new ArrayList<>());
        conditions.setInferredDefaults(new ArrayList<>());
        return conditions;
    }

    private ParsedConditionsDTO createConditionsSpotType(String spotType) {
        ParsedConditionsDTO conditions = new ParsedConditionsDTO();
        conditions.setHardConditions(new ArrayList<>());
        ParsedConditionsDTO.HardCondition hc = new ParsedConditionsDTO.HardCondition();
        hc.setType("spot_type");
        hc.setValue(spotType);
        hc.setSource("explicit");
        conditions.getHardConditions().add(hc);
        conditions.setSoftPreferences(new ArrayList<>());
        conditions.setConfidence(0.9);
        conditions.setAmbiguousTerms(new ArrayList<>());
        conditions.setRequiresConfirmation(new ArrayList<>());
        conditions.setInferredDefaults(new ArrayList<>());
        return conditions;
    }

    private User createUser() {
        User user = new User();
        user.setUserId("U001");
        return user;
    }

    private boolean hasFilterLogWithReason(HardConditionFilter.FilterResult result,
                                           String spotId, String reasonCode) {
        return result.getFilterLogs().stream()
                .anyMatch(log -> spotId.equals(log.getSpotId())
                        && reasonCode.equals(log.getReason()));
    }

    // ==================== 1. Vehicle height 2.10m, limit 2.10m -> pass ====================
    @Test
    @DisplayName("1. Vehicle height 2.10m, limit 2.10m -> pass (equal passes)")
    void heightEqualLimitShouldPass() {
        ParkingSpot spot = createSpot("S001", "available", HEIGHT_2_10, false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));
        ParsedConditionsDTO conditions = createConditionsWithHeight(HEIGHT_2_10);
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertEquals(1, result.getPassed().size());
        assertEquals("S001", result.getPassed().get(0).getSpotId());
    }

    // ==================== 2. Vehicle height 2.11m, limit 2.10m -> filtered ====================
    @Test
    @DisplayName("2. Vehicle height 2.11m, limit 2.10m -> filtered (exceeds limit)")
    void heightExceedsLimitShouldFilter() {
        ParkingSpot spot = createSpot("S002", "available", HEIGHT_2_10, false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));
        ParsedConditionsDTO conditions = createConditionsWithHeight(HEIGHT_2_11);
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertTrue(result.getPassed().isEmpty());
        assertTrue(hasFilterLogWithReason(result, "S002", "HEIGHT_LIMIT_NOT_ENOUGH"));
    }

    // ==================== 3. Occupied spot -> filtered ====================
    @Test
    @DisplayName("3. Occupied spot -> filtered")
    void occupiedSpotShouldBeFiltered() {
        ParkingSpot spot = createSpot("S003", "occupied", HEIGHT_2_30, false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));
        ParsedConditionsDTO conditions = createEmptyConditions();
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertTrue(result.getPassed().isEmpty());
        assertTrue(hasFilterLogWithReason(result, "S003", "STATUS_NOT_AVAILABLE"));
    }

    // ==================== 4. Reserved spot -> filtered ====================
    @Test
    @DisplayName("4. Reserved spot -> filtered")
    void reservedSpotShouldBeFiltered() {
        ParkingSpot spot = createSpot("S004", "reserved", HEIGHT_2_30, false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));
        ParsedConditionsDTO conditions = createEmptyConditions();
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertTrue(result.getPassed().isEmpty());
        assertTrue(hasFilterLogWithReason(result, "S004", "STATUS_NOT_AVAILABLE"));
    }

    // ==================== 5. Maintenance spot -> filtered ====================
    @Test
    @DisplayName("5. Maintenance spot -> filtered")
    void maintenanceSpotShouldBeFiltered() {
        ParkingSpot spot = createSpot("S005", "maintenance", HEIGHT_2_30, false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));
        ParsedConditionsDTO conditions = createEmptyConditions();
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertTrue(result.getPassed().isEmpty());
        assertTrue(hasFilterLogWithReason(result, "S005", "STATUS_NOT_AVAILABLE"));
    }

    // ==================== 6. Null status -> filtered ====================
    @Test
    @DisplayName("6. Null status -> filtered")
    void nullStatusShouldBeFiltered() {
        ParkingSpot spot = createSpot("S006", null, HEIGHT_2_30, false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));
        ParsedConditionsDTO conditions = createEmptyConditions();
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertTrue(result.getPassed().isEmpty());
        assertTrue(hasFilterLogWithReason(result, "S006", "STATUS_NOT_AVAILABLE"));
    }

    // ==================== 7. Must charge -> filter no-charging spot ====================
    @Test
    @DisplayName("7. Must charge -> filter spot without charging")
    void mustChargeFilterNoChargingSpot() {
        ParkingSpot spot = createSpot("S007", "available", HEIGHT_2_30, false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));
        ParsedConditionsDTO conditions = createConditionsChargingRequired();
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertTrue(result.getPassed().isEmpty());
        assertTrue(hasFilterLogWithReason(result, "S007", "CHARGING_REQUIRED"));
    }

    // ==================== 8. Soft charging preference -> no hard filter ====================
    @Test
    @DisplayName("8. Soft charging preference -> spot without charging passes")
    void softChargingPreferenceNoHardFilter() {
        ParkingSpot spot = createSpot("S008", "available", HEIGHT_2_30, false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));

        // Soft preference only, no hard condition for charging
        ParsedConditionsDTO conditions = createEmptyConditions();
        conditions.setSoftPreferences(new ArrayList<>());
        ParsedConditionsDTO.SoftPreference sp = new ParsedConditionsDTO.SoftPreference();
        sp.setType("charging");
        sp.setWeight(0.5);
        sp.setSource("explicit");
        conditions.getSoftPreferences().add(sp);

        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertEquals(1, result.getPassed().size());
        assertEquals("S008", result.getPassed().get(0).getSpotId());
    }

    // ==================== 9. Spot type mismatch -> filtered ====================
    @Test
    @DisplayName("9. Spot type mismatch -> filtered")
    void spotTypeMismatchShouldFilter() {
        ParkingSpot spot = createSpot("S009", "available", HEIGHT_2_30, true, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));
        ParsedConditionsDTO conditions = createConditionsSpotType("compact");
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertTrue(result.getPassed().isEmpty());
        assertTrue(hasFilterLogWithReason(result, "S009", "SPOT_TYPE_NOT_ALLOWED"));
    }

    // ==================== 10. Conditions height > User height -> use conditions (stricter) ====================
    @Test
    @DisplayName("10. Conditions height 2.30 > User height 2.10 -> use 2.30, filter spot with limit 2.20")
    void conditionsHigherThanUserUseConditionsHeight() {
        ParkingSpot spot = createSpot("S010", "available", new BigDecimal("2.20"), false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));
        ParsedConditionsDTO conditions = createConditionsWithHeight(HEIGHT_2_30);
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertTrue(result.getPassed().isEmpty());
        assertTrue(hasFilterLogWithReason(result, "S010", "HEIGHT_LIMIT_NOT_ENOUGH"));
    }

    // ==================== 11. User height > conditions height -> use User (stricter) ====================
    @Test
    @DisplayName("11. User height 2.30 > Conditions height 2.10 -> use 2.30, filter spot with limit 2.20")
    void userHigherThanConditionsUseUserHeight() {
        // User.vehicleHeight removed — now only conditions height is used
        // Spot height 2.20 >= conditions height 2.10, so it should pass
        ParkingSpot spot = createSpot("S011", "available", new BigDecimal("2.20"), false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));
        ParsedConditionsDTO conditions = createConditionsWithHeight(HEIGHT_2_10);
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertFalse(result.getPassed().isEmpty());
        assertEquals("S011", result.getPassed().get(0).getSpotId());
    }

    // ==================== 12. Height limit missing -> cannot default pass ====================
    @Test
    @DisplayName("12. Height limit null, conditions requires height -> must filter (REQUIRED_FIELD_MISSING)")
    void heightLimitNullWithConditionRequiresHeightMustFilter() {
        ParkingSpot spot = createSpot("S012", "available", null, false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));
        ParsedConditionsDTO conditions = createConditionsWithHeight(HEIGHT_2_10);
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertTrue(result.getPassed().isEmpty());
        assertTrue(hasFilterLogWithReason(result, "S012", "HEIGHT_LIMIT_NOT_ENOUGH"));
    }

    // ==================== 13. Candidates null -> return empty ====================
    @Test
    @DisplayName("13. Candidates null -> return empty result")
    void nullCandidatesReturnsEmpty() {
        ParsedConditionsDTO conditions = createEmptyConditions();
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(null, conditions, user);

        assertNotNull(result);
        assertNotNull(result.getPassed());
        assertTrue(result.getPassed().isEmpty());
        assertNotNull(result.getFilterLogs());
        assertTrue(result.getFilterLogs().isEmpty());
    }

    // ==================== 14. Conditions null -> only status check ====================
    @Test
    @DisplayName("14. Conditions null -> only status check")
    void nullConditionsOnlyStatusCheck() {
        ParkingSpot availableSpot = createSpot("S014a", "available", HEIGHT_2_30, false, "standard");
        ParkingSpot occupiedSpot = createSpot("S014b", "occupied", HEIGHT_2_30, false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(availableSpot, occupiedSpot));
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, null, user);

        assertEquals(1, result.getPassed().size());
        assertEquals("S014a", result.getPassed().get(0).getSpotId());
        assertTrue(hasFilterLogWithReason(result, "S014b", "STATUS_NOT_AVAILABLE"));
    }

    // ==================== 15. Invalid height value "abc" -> INVALID_CONDITION ====================
    @Test
    @DisplayName("15. Invalid height value 'abc' -> INVALID_CONDITION logged, not blocked by invalid value")
    void invalidHeightValueLogsInvalidCondition() {
        // Invalid height value -> extractHeightFromConditions returns null -> no filtering
        ParkingSpot spot = createSpot("S015", "available", HEIGHT_2_30, false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));
        ParsedConditionsDTO conditions = createConditionsWithHeight(new BigDecimal("2.10"));
        conditions.getHardConditions().get(0).setValue("abc");
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        // Spot should pass: invalid height value means no height check
        assertEquals(1, result.getPassed().size());
    }

    // ==================== 16. Qualification not verifiable -> QUALIFICATION_NOT_VERIFIABLE ====================
    @Test
    @DisplayName("16. Qualification hard condition, ParkingSpot has no qualification field -> fail-closed")
    void qualificationNotVerifiableShouldFilter() {
        ParkingSpot spot = createSpot("S016", "available", HEIGHT_2_30, true, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));

        ParsedConditionsDTO conditions = createEmptyConditions();
        ParsedConditionsDTO.HardCondition hc = new ParsedConditionsDTO.HardCondition();
        hc.setType("qualification");
        hc.setValue("visitor");
        hc.setSource("explicit");
        conditions.getHardConditions().add(hc);

        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertTrue(result.getPassed().isEmpty());
        assertTrue(hasFilterLogWithReason(result, "S016", "QUALIFICATION_NOT_VERIFIABLE"));
    }

    // ==================== 17. AI error -> still execute confirmed conditions ====================
    @Test
    @DisplayName("17. AI output error -> still filter by confirmed conditions (height 2.30 > limit 2.20)")
    void aiErrorStillExecutesConfirmedConditions() {
        ParkingSpot spot = createSpot("S017", "available", new BigDecimal("2.20"), false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));
        ParsedConditionsDTO conditions = createConditionsWithHeight(HEIGHT_2_30);
        conditions.setConfidence(0.1); // Low confidence = AI error
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        // Filter executes confirmed conditions regardless of AI confidence
        assertTrue(result.getPassed().isEmpty());
        assertTrue(hasFilterLogWithReason(result, "S017", "HEIGHT_LIMIT_NOT_ENOUGH"));
    }

    // ==================== 18. Null element in candidates -> skipped ====================
    @Test
    @DisplayName("18. Null element in candidates -> skipped, other spots processed normally")
    void nullElementInCandidatesSkipped() {
        ParkingSpot spot = createSpot("S018a", "available", HEIGHT_2_30, false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>();
        candidates.add(null);
        candidates.add(spot);
        ParsedConditionsDTO conditions = createEmptyConditions();
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertEquals(1, result.getPassed().size());
        assertEquals("S018a", result.getPassed().get(0).getSpotId());
    }

    // ==================== 19. Input list not modified ====================
    @Test
    @DisplayName("19. Input list not modified by filter")
    void inputListNotModified() {
        ParkingSpot spot1 = createSpot("S019a", "available", HEIGHT_2_30, false, "standard");
        ParkingSpot spot2 = createSpot("S019b", "occupied", HEIGHT_2_30, false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(Arrays.asList(spot1, spot2));
        int originalSize = candidates.size();

        ParsedConditionsDTO conditions = createEmptyConditions();
        User user = createUser();

        filter.filter(candidates, conditions, user);

        assertEquals(originalSize, candidates.size(), "Input list should not be modified");
        assertEquals("S019a", candidates.get(0).getSpotId());
        assertEquals("S019b", candidates.get(1).getSpotId());
    }

    // ==================== 20. Filter log contains correct spotId, reason code, rule type ====================
    @Test
    @DisplayName("20. Filter logs contain correct spotId, reason code and rule type")
    void filterLogsContainCorrectInfo() {
        ParkingSpot spot = createSpot("S020", "available", HEIGHT_2_10, false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));
        ParsedConditionsDTO conditions = createConditionsWithHeight(HEIGHT_2_11);
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertTrue(result.getPassed().isEmpty());
        assertFalse(result.getFilterLogs().isEmpty(), "Filter logs should not be empty");

        HardConditionFilter.FilterLog log = result.getFilterLogs().stream()
                .filter(l -> "S020".equals(l.getSpotId()))
                .findFirst()
                .orElse(null);

        assertNotNull(log, "Log for S020 should exist");
        assertEquals("S020", log.getSpotId());
        assertEquals("HEIGHT_LIMIT_NOT_ENOUGH", log.getReason());
        assertEquals("HEIGHT", log.getRuleType());
        assertFalse(log.isPassed());
        assertNotNull(log.getTimestamp());
    }

    // ==================== FILTER-P2-01: Filter logs contain expected/actual values ====================
    @Test
    @DisplayName("FILTER-P2-01a: Height filter log contains expected/actual values")
    void heightFilterLogContainsExpectedActual() {
        ParkingSpot spot = createSpot("S_EA1", "available", HEIGHT_2_10, false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));
        ParsedConditionsDTO conditions = createConditionsWithHeight(HEIGHT_2_11);
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertTrue(result.getPassed().isEmpty());
        HardConditionFilter.FilterLog log = result.getFilterLogs().stream()
                .filter(l -> "S_EA1".equals(l.getSpotId()) && "HEIGHT_LIMIT_NOT_ENOUGH".equals(l.getReason()))
                .findFirst()
                .orElse(null);
        assertNotNull(log, "Log for S_EA1 with HEIGHT_LIMIT_NOT_ENOUGH should exist");
        assertNotNull(log.getExpectedValue());
        assertNotNull(log.getActualValue());
        assertTrue(log.getExpectedValue().contains("2.11"));
        assertTrue(log.getActualValue().contains("2.10"));
    }

    @Test
    @DisplayName("FILTER-P2-01b: Status filter log contains expected/actual values")
    void statusFilterLogContainsExpectedActual() {
        ParkingSpot spot = createSpot("S_EA2", "occupied", HEIGHT_2_30, false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));
        ParsedConditionsDTO conditions = createEmptyConditions();
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertTrue(result.getPassed().isEmpty());
        HardConditionFilter.FilterLog log = result.getFilterLogs().stream()
                .filter(l -> "S_EA2".equals(l.getSpotId()) && "STATUS_NOT_AVAILABLE".equals(l.getReason()))
                .findFirst()
                .orElse(null);
        assertNotNull(log);
        assertEquals("available", log.getExpectedValue());
        assertEquals("occupied", log.getActualValue());
    }

    @Test
    @DisplayName("FILTER-P2-01c: Charging filter log contains expected/actual values")
    void chargingFilterLogContainsExpectedActual() {
        ParkingSpot spot = createSpot("S_EA3", "available", HEIGHT_2_30, false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));
        ParsedConditionsDTO conditions = createConditionsChargingRequired();
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertTrue(result.getPassed().isEmpty());
        HardConditionFilter.FilterLog log = result.getFilterLogs().stream()
                .filter(l -> "S_EA3".equals(l.getSpotId()) && "CHARGING_REQUIRED".equals(l.getReason()))
                .findFirst()
                .orElse(null);
        assertNotNull(log);
        assertEquals("true", log.getExpectedValue());
        assertEquals("false", log.getActualValue());
    }

    @Test
    @DisplayName("FILTER-P2-01d: Spot type filter log contains expected/actual values")
    void spotTypeFilterLogContainsExpectedActual() {
        ParkingSpot spot = createSpot("S_EA4", "available", HEIGHT_2_30, true, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));
        ParsedConditionsDTO conditions = createConditionsSpotType("compact");
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertTrue(result.getPassed().isEmpty());
        HardConditionFilter.FilterLog log = result.getFilterLogs().stream()
                .filter(l -> "S_EA4".equals(l.getSpotId()) && "SPOT_TYPE_NOT_ALLOWED".equals(l.getReason()))
                .findFirst()
                .orElse(null);
        assertNotNull(log);
        assertEquals("compact", log.getExpectedValue());
        assertEquals("standard", log.getActualValue());
    }

    @Test
    @DisplayName("FILTER-P2-01e: REQUIRED_FIELD_MISSING log contains expected/actual values")
    void requiredFieldMissingLogContainsExpectedActual() {
        // Height limit null with conditions requiring height -> HEIGHT_LIMIT_NOT_ENOUGH
        ParkingSpot spot = createSpot("S_EA5", "available", null, false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));
        ParsedConditionsDTO conditions = createConditionsWithHeight(HEIGHT_2_10);
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertTrue(result.getPassed().isEmpty());
        HardConditionFilter.FilterLog log = result.getFilterLogs().stream()
                .filter(l -> "S_EA5".equals(l.getSpotId()) && "HEIGHT_LIMIT_NOT_ENOUGH".equals(l.getReason()))
                .findFirst()
                .orElse(null);
        assertNotNull(log);
        assertEquals(">=2.10m", log.getExpectedValue());
        assertEquals("null", log.getActualValue());
    }

    // ==================== Additional tests for completeness ====================

    @Test
    @DisplayName("Unknown status -> filtered")
    void unknownStatusShouldBeFiltered() {
        ParkingSpot spot = createSpot("S_UNK", "unknown_status", HEIGHT_2_30, false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));
        ParsedConditionsDTO conditions = createEmptyConditions();
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertTrue(result.getPassed().isEmpty());
        assertTrue(hasFilterLogWithReason(result, "S_UNK", "STATUS_NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("Must charge, spot has charging -> pass")
    void mustChargeWithChargingSpotShouldPass() {
        ParkingSpot spot = createSpot("S_CHG", "available", HEIGHT_2_30, true, "charging");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));
        ParsedConditionsDTO conditions = createConditionsChargingRequired();
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertEquals(1, result.getPassed().size());
        assertEquals("S_CHG", result.getPassed().get(0).getSpotId());
    }

    @Test
    @DisplayName("Spot type matches -> pass")
    void spotTypeMatchShouldPass() {
        ParkingSpot spot = createSpot("S_TPE", "available", HEIGHT_2_30, true, "compact");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));
        ParsedConditionsDTO conditions = createConditionsSpotType("compact");
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertEquals(1, result.getPassed().size());
        assertEquals("S_TPE", result.getPassed().get(0).getSpotId());
    }

    @Test
    @DisplayName("Accessibility hard condition, ParkingSpot has no accessibility field -> fail-closed")
    void accessibilityNotVerifiableShouldFilter() {
        ParkingSpot spot = createSpot("S_ACC", "available", HEIGHT_2_30, true, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));

        ParsedConditionsDTO conditions = createEmptyConditions();
        ParsedConditionsDTO.HardCondition hc = new ParsedConditionsDTO.HardCondition();
        hc.setType("accessibility");
        hc.setValue("true");
        hc.setSource("explicit");
        conditions.getHardConditions().add(hc);

        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertTrue(result.getPassed().isEmpty());
        assertTrue(hasFilterLogWithReason(result, "S_ACC", "ACCESSIBILITY_NOT_VERIFIABLE"));
    }

    // ==================== FILTER-P2-02: Accessibility independent reason code ====================
    @Test
    @DisplayName("FILTER-P2-02: Accessibility hard condition -> ACCESSIBILITY_NOT_VERIFIABLE + RULE_ACCESSIBILITY")
    void accessibilityIndependentReasonCode() {
        ParkingSpot spot = createSpot("S_ACC2", "available", HEIGHT_2_30, true, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));

        ParsedConditionsDTO conditions = createEmptyConditions();
        ParsedConditionsDTO.HardCondition hc = new ParsedConditionsDTO.HardCondition();
        hc.setType("accessibility");
        hc.setValue("wheelchair");
        hc.setSource("explicit");
        conditions.getHardConditions().add(hc);

        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertTrue(result.getPassed().isEmpty());
        assertTrue(hasFilterLogWithReason(result, "S_ACC2", "ACCESSIBILITY_NOT_VERIFIABLE"));

        // Verify rule type is ACCESSIBILITY (not QUALIFICATION)
        HardConditionFilter.FilterLog log = result.getFilterLogs().stream()
                .filter(l -> "S_ACC2".equals(l.getSpotId()) && "ACCESSIBILITY_NOT_VERIFIABLE".equals(l.getReason()))
                .findFirst()
                .orElse(null);
        assertNotNull(log, "Log for S_ACC2 with ACCESSIBILITY_NOT_VERIFIABLE should exist");
        assertEquals("ACCESSIBILITY", log.getRuleType());
        assertFalse(log.isPassed());
    }

    @Test
    @DisplayName("Conditions has no height -> no height filtering applied")
    void userHeightOnlyStillFilters() {
        // User.vehicleHeight removed — no height in conditions means no height check
        ParkingSpot spot = createSpot("S_UH", "available", HEIGHT_2_10, false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));
        ParsedConditionsDTO conditions = createEmptyConditions();
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        // Spot should pass: no height condition means no height filtering
        assertFalse(result.getPassed().isEmpty());
        assertEquals("S_UH", result.getPassed().get(0).getSpotId());
    }

    @Test
    @DisplayName("Condition height unparseable -> no height filtering (passes)")
    void conditionHeightInvalidFallsBackToUserHeight() {
        // User.vehicleHeight removed — invalid condition height means no height check
        ParkingSpot spot = createSpot("S_FBF", "available", HEIGHT_2_10, false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));
        ParsedConditionsDTO conditions = createConditionsWithHeight(HEIGHT_2_30);
        conditions.getHardConditions().get(0).setValue("not_a_number");
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        // Spot should pass: invalid height value means no height check
        assertFalse(result.getPassed().isEmpty());
        assertEquals("S_FBF", result.getPassed().get(0).getSpotId());
    }

    @Test
    @DisplayName("Multiple spots with mixed results -> correct pass/fail")
    void multipleSpotsMixedResults() {
        ParkingSpot spotPass = createSpot("S_P1", "available", HEIGHT_2_30, false, "standard");
        ParkingSpot spotFail = createSpot("S_F1", "available", HEIGHT_2_10, false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(Arrays.asList(spotPass, spotFail));
        ParsedConditionsDTO conditions = createConditionsWithHeight(HEIGHT_2_11);
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertEquals(1, result.getPassed().size());
        assertEquals("S_P1", result.getPassed().get(0).getSpotId());
        assertTrue(hasFilterLogWithReason(result, "S_F1", "HEIGHT_LIMIT_NOT_ENOUGH"));
    }

    @Test
    @DisplayName("Empty candidates list -> empty result")
    void emptyCandidatesReturnsEmpty() {
        List<ParkingSpot> candidates = new ArrayList<>();
        ParsedConditionsDTO conditions = createEmptyConditions();
        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertNotNull(result);
        assertTrue(result.getPassed().isEmpty());
        assertTrue(result.getFilterLogs().isEmpty());
    }

    // ==================== FILTER-P0-01: vehicle_length fail-closed ====================
    @Test
    @DisplayName("FILTER-P0-01a: vehicle_length hard condition -> fail-closed (VEHICLE_LENGTH_NOT_VERIFIABLE)")
    void vehicleLengthNotVerifiableShouldFilter() {
        ParkingSpot spot = createSpot("S_VL", "available", HEIGHT_2_30, true, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));

        ParsedConditionsDTO conditions = createEmptyConditions();
        ParsedConditionsDTO.HardCondition hc = new ParsedConditionsDTO.HardCondition();
        hc.setType("vehicle_length");
        hc.setValue("5.0");
        hc.setSource("explicit");
        conditions.getHardConditions().add(hc);

        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertTrue(result.getPassed().isEmpty(),
                "vehicle_length should cause fail-closed filtering");
        assertTrue(hasFilterLogWithReason(result, "S_VL", "VEHICLE_LENGTH_NOT_VERIFIABLE"));
    }

    // ==================== FILTER-P0-01b: vehicle_width fail-closed ====================
    @Test
    @DisplayName("FILTER-P0-01b: vehicle_width hard condition -> fail-closed (VEHICLE_WIDTH_NOT_VERIFIABLE)")
    void vehicleWidthNotVerifiableShouldFilter() {
        ParkingSpot spot = createSpot("S_VW", "available", HEIGHT_2_30, true, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));

        ParsedConditionsDTO conditions = createEmptyConditions();
        ParsedConditionsDTO.HardCondition hc = new ParsedConditionsDTO.HardCondition();
        hc.setType("vehicle_width");
        hc.setValue("2.0");
        hc.setSource("explicit");
        conditions.getHardConditions().add(hc);

        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertTrue(result.getPassed().isEmpty(),
                "vehicle_width should cause fail-closed filtering");
        assertTrue(hasFilterLogWithReason(result, "S_VW", "VEHICLE_WIDTH_NOT_VERIFIABLE"));
    }

    // ==================== FILTER-P0-02: charging value="required" -> not trigger filter ====================
    @Test
    @DisplayName("FILTER-P0-02a: charging value='required' -> does not trigger charging filter")
    void chargingValueRequiredDoesNotTriggerFilter() {
        ParkingSpot spot = createSpot("S_REQ", "available", HEIGHT_2_30, false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));

        ParsedConditionsDTO conditions = createEmptyConditions();
        ParsedConditionsDTO.HardCondition hc = new ParsedConditionsDTO.HardCondition();
        hc.setType("charging");
        hc.setValue("required");
        hc.setSource("explicit");
        conditions.getHardConditions().add(hc);

        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertEquals(1, result.getPassed().size(),
                "charging value='required' should NOT trigger charging filter (only 'true' does)");
        assertEquals("S_REQ", result.getPassed().get(0).getSpotId());
    }

    // ==================== FILTER-P0-02b: charging value="yes" -> not trigger filter ====================
    @Test
    @DisplayName("FILTER-P0-02b: charging value='yes' -> does not trigger charging filter")
    void chargingValueYesDoesNotTriggerFilter() {
        ParkingSpot spot = createSpot("S_YES", "available", HEIGHT_2_30, false, "standard");
        List<ParkingSpot> candidates = new ArrayList<>(List.of(spot));

        ParsedConditionsDTO conditions = createEmptyConditions();
        ParsedConditionsDTO.HardCondition hc = new ParsedConditionsDTO.HardCondition();
        hc.setType("charging");
        hc.setValue("yes");
        hc.setSource("explicit");
        conditions.getHardConditions().add(hc);

        User user = createUser();

        HardConditionFilter.FilterResult result = filter.filter(candidates, conditions, user);

        assertEquals(1, result.getPassed().size(),
                "charging value='yes' should NOT trigger charging filter (only 'true' does)");
        assertEquals("S_YES", result.getPassed().get(0).getSpotId());
    }
}
