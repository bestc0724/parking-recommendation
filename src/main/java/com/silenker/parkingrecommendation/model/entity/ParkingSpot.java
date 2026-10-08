package com.silenker.parkingrecommendation.model.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ParkingSpot {
    private String spotId;
    private BigDecimal heightLimit;
    private Boolean hasCharging;
    private String spotType;
    private Integer distanceToElevator;
    private BigDecimal pricePerHour;
    private String status;
    private Integer version;
    private LocalDateTime updatedAt;
}
