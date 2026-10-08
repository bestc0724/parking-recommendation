package com.silenker.parkingrecommendation.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class ParsedConditionsDTO {
    @JsonProperty("hard_conditions")
    private List<HardCondition> hardConditions;

    @JsonProperty("soft_preferences")
    private List<SoftPreference> softPreferences;

    private Double confidence;

    @JsonProperty("ambiguous_terms")
    private List<String> ambiguousTerms;

    @JsonProperty("requires_confirmation")
    private List<String> requiresConfirmation;

    @JsonProperty("inferred_defaults")
    private List<String> inferredDefaults;

    @Data
    public static class HardCondition {
        private String type;
        private String value;
        private String unit;
        private String source;
    }

    @Data
    public static class SoftPreference {
        private String type;
        private Double weight;
        private String value;
        private String source;
    }
}
