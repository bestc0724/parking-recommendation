package com.silenker.parkingrecommendation.core.rule;

import com.silenker.parkingrecommendation.model.dto.ParsedConditionsDTO;
import com.silenker.parkingrecommendation.model.entity.ParkingSpot;
import com.silenker.parkingrecommendation.model.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hard condition filter — the gatekeeper of the filtering layer.
 * No LLM calls, no confidence-based bypassing.
 */
@Slf4j
@Component
public class HardConditionFilter {

    public static final String STATUS_NOT_AVAILABLE = "STATUS_NOT_AVAILABLE";
    public static final String HEIGHT_LIMIT_NOT_ENOUGH = "HEIGHT_LIMIT_NOT_ENOUGH";
    public static final String CHARGING_REQUIRED = "CHARGING_REQUIRED";
    public static final String SPOT_TYPE_NOT_ALLOWED = "SPOT_TYPE_NOT_ALLOWED";
    public static final String QUALIFICATION_NOT_VERIFIABLE = "QUALIFICATION_NOT_VERIFIABLE";
    public static final String ACCESSIBILITY_NOT_VERIFIABLE = "ACCESSIBILITY_NOT_VERIFIABLE";
    public static final String VEHICLE_LENGTH_NOT_VERIFIABLE = "VEHICLE_LENGTH_NOT_VERIFIABLE";
    public static final String VEHICLE_WIDTH_NOT_VERIFIABLE = "VEHICLE_WIDTH_NOT_VERIFIABLE";

    public FilterResult filter(List<ParkingSpot> candidates,
                               ParsedConditionsDTO conditions,
                               User user) {
        if (candidates == null) {
            return new FilterResult(Collections.emptyList(), Collections.emptyList());
        }

        List<ParkingSpot> passed = new ArrayList<>();
        List<FilterLog> logs = new ArrayList<>();

        for (ParkingSpot spot : candidates) {
            if (spot == null) continue;
            boolean spotPassed = checkSpot(spot, conditions, logs);
            if (spotPassed) {
                passed.add(spot);
                logs.add(new FilterLog(spot.getSpotId(), true, "PASSED", "ALL", LocalDateTime.now(), null, null));
            }
        }

        log.info("HardConditionFilter: {} candidates -> {} passed", candidates.size(), passed.size());
        return new FilterResult(passed, logs);
    }

    private boolean checkSpot(ParkingSpot spot, ParsedConditionsDTO conditions, List<FilterLog> logs) {
        if (!checkStatus(spot, logs)) return false;
        if (conditions != null) {
            if (!checkHeight(spot, conditions, logs)) return false;
            if (!checkCharging(spot, conditions, logs)) return false;
            if (!checkSpotType(spot, conditions, logs)) return false;
            if (!checkUnsupportedConditions(spot, conditions, logs)) return false;
        }
        return true;
    }

    private boolean checkStatus(ParkingSpot spot, List<FilterLog> logs) {
        if (!"available".equals(spot.getStatus())) {
            addLog(logs, spot.getSpotId(), false, STATUS_NOT_AVAILABLE, "STATUS", "available", spot.getStatus());
            return false;
        }
        return true;
    }

    private boolean checkHeight(ParkingSpot spot, ParsedConditionsDTO conditions, List<FilterLog> logs) {
        BigDecimal requiredHeight = extractHeightFromConditions(conditions);
        if (requiredHeight == null) return true;

        if (spot.getHeightLimit() == null) {
            addLog(logs, spot.getSpotId(), false, HEIGHT_LIMIT_NOT_ENOUGH, "HEIGHT", ">=" + requiredHeight + "m", "null");
            return false;
        }

        if (spot.getHeightLimit().compareTo(requiredHeight) < 0) {
            addLog(logs, spot.getSpotId(), false, HEIGHT_LIMIT_NOT_ENOUGH, "HEIGHT",
                    ">=" + requiredHeight + "m", spot.getHeightLimit() + "m");
            return false;
        }
        return true;
    }

    private boolean checkCharging(ParkingSpot spot, ParsedConditionsDTO conditions, List<FilterLog> logs) {
        Boolean needCharging = extractChargingNeed(conditions);
        if (Boolean.TRUE.equals(needCharging)) {
            if (!Boolean.TRUE.equals(spot.getHasCharging())) {
                addLog(logs, spot.getSpotId(), false, CHARGING_REQUIRED, "CHARGING", "true", String.valueOf(spot.getHasCharging()));
                return false;
            }
        }
        return true;
    }

    private boolean checkSpotType(ParkingSpot spot, ParsedConditionsDTO conditions, List<FilterLog> logs) {
        String requiredType = extractSpotType(conditions);
        if (requiredType != null) {
            if (!requiredType.equals(spot.getSpotType())) {
                addLog(logs, spot.getSpotId(), false, SPOT_TYPE_NOT_ALLOWED, "SPOT_TYPE", requiredType, spot.getSpotType());
                return false;
            }
        }
        return true;
    }

    /**
     * Check unsupported conditions — fail-closed for conditions that can't be verified.
     */
    private boolean checkUnsupportedConditions(ParkingSpot spot, ParsedConditionsDTO conditions, List<FilterLog> logs) {
        if (conditions.getHardConditions() == null) return true;
        for (ParsedConditionsDTO.HardCondition hc : conditions.getHardConditions()) {
            if (hc.getType() == null) continue;
            switch (hc.getType()) {
                case "qualification" -> {
                    addLog(logs, spot.getSpotId(), false, QUALIFICATION_NOT_VERIFIABLE, "QUALIFICATION", "verifiable", "not verifiable");
                    return false;
                }
                case "accessibility" -> {
                    addLog(logs, spot.getSpotId(), false, ACCESSIBILITY_NOT_VERIFIABLE, "ACCESSIBILITY", "verifiable", "not verifiable");
                    return false;
                }
                case "vehicle_length" -> {
                    addLog(logs, spot.getSpotId(), false, VEHICLE_LENGTH_NOT_VERIFIABLE, "VEHICLE_LENGTH", "supported", "not supported");
                    return false;
                }
                case "vehicle_width" -> {
                    addLog(logs, spot.getSpotId(), false, VEHICLE_WIDTH_NOT_VERIFIABLE, "VEHICLE_WIDTH", "supported", "not supported");
                    return false;
                }
            }
        }
        return true;
    }

    private BigDecimal extractHeightFromConditions(ParsedConditionsDTO conditions) {
        if (conditions == null || conditions.getHardConditions() == null) return null;
        for (ParsedConditionsDTO.HardCondition hc : conditions.getHardConditions()) {
            if ("height".equals(hc.getType()) && hc.getValue() != null) {
                try { return new BigDecimal(hc.getValue()); } catch (NumberFormatException e) { return null; }
            }
        }
        return null;
    }

    private Boolean extractChargingNeed(ParsedConditionsDTO conditions) {
        if (conditions == null || conditions.getHardConditions() == null) return null;
        for (ParsedConditionsDTO.HardCondition hc : conditions.getHardConditions()) {
            if ("charging".equals(hc.getType()) && "true".equalsIgnoreCase(hc.getValue())) return true;
        }
        return null;
    }

    private String extractSpotType(ParsedConditionsDTO conditions) {
        if (conditions == null || conditions.getHardConditions() == null) return null;
        for (ParsedConditionsDTO.HardCondition hc : conditions.getHardConditions()) {
            if ("spot_type".equals(hc.getType()) && hc.getValue() != null) return hc.getValue();
        }
        return null;
    }

    private void addLog(List<FilterLog> logs, String spotId, boolean passed, String reason, String ruleType, String expected, String actual) {
        logs.add(new FilterLog(spotId, passed, reason, ruleType, LocalDateTime.now(), expected, actual));
    }

    public static class FilterResult {
        private final List<ParkingSpot> passed;
        private final List<FilterLog> filterLogs;
        public FilterResult(List<ParkingSpot> passed, List<FilterLog> filterLogs) {
            this.passed = passed != null ? passed : Collections.emptyList();
            this.filterLogs = filterLogs != null ? filterLogs : Collections.emptyList();
        }
        public List<ParkingSpot> getPassed() { return passed; }
        public List<FilterLog> getFilterLogs() { return filterLogs; }
    }

    public static class FilterLog {
        private final String spotId;
        private final boolean passed;
        private final String reason;
        private final String ruleType;
        private final LocalDateTime timestamp;
        private final String expectedValue;
        private final String actualValue;
        public FilterLog(String spotId, boolean passed, String reason, String ruleType, LocalDateTime timestamp, String expected, String actual) {
            this.spotId = spotId; this.passed = passed; this.reason = reason; this.ruleType = ruleType;
            this.timestamp = timestamp; this.expectedValue = expected; this.actualValue = actual;
        }
        public String getSpotId() { return spotId; }
        public boolean isPassed() { return passed; }
        public String getReason() { return reason; }
        public String getRuleType() { return ruleType; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getExpectedValue() { return expectedValue; }
        public String getActualValue() { return actualValue; }
    }
}
