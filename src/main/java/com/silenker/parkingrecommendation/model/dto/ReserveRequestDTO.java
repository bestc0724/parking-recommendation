package com.silenker.parkingrecommendation.model.dto;

import lombok.Data;

@Data
public class ReserveRequestDTO {
    private String userId;
    private String spotId;
}
