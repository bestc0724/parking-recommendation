package com.silenker.parkingrecommendation.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class ConfirmParseDTO {
    private String requestId;
    private String userId;
    private List<ParsedConditionsDTO.HardCondition> hardConditions;
    private List<ParsedConditionsDTO.SoftPreference> softPreferences;
}
