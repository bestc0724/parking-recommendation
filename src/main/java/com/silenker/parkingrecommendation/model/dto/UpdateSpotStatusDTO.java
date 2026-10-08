package com.silenker.parkingrecommendation.model.dto;

import lombok.Data;

@Data
public class UpdateSpotStatusDTO {
    private String status;
    private String reason;
}
