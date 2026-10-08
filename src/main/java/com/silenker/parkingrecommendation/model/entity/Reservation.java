package com.silenker.parkingrecommendation.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Reservation {
    private String reservationId;
    private String spotId;
    private String userId;
    private String status; // pending/confirmed/expired/cancelled
    private LocalDateTime reservedAt;
    private LocalDateTime expiresAt;
}
