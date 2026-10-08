package com.silenker.parkingrecommendation.model.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;

@Data
public class RecommendResultDTO {
    private String spotId;
    private BigDecimal heightLimit;
    private Boolean hasCharging;
    private String spotType;
    private Integer distanceToElevator;
    private BigDecimal pricePerHour;
    private Double score;
    private Map<String, Double> scoreDetails;
    private Map<String, Object> scoreBreakdown;
    private String reason;
}
