package com.silenker.parkingrecommendation.model.dto;

import lombok.Data;

@Data
public class DriverRequestDTO {
    private String userId;
    private String vehicleId;
    private String query;
}
