package com.silenker.parkingrecommendation.service;

import com.silenker.parkingrecommendation.model.entity.ParkingSpot;

import java.util.List;

public interface ISpotService {
    List<ParkingSpot> getAllSpots();
    ParkingSpot getById(String spotId);
    List<ParkingSpot> getAvailableSpots();
    boolean updateStatus(String spotId, String status);
    boolean updateStatusWithLock(String spotId, String status, int expectedVersion);
}
