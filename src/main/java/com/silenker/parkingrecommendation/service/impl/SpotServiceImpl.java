package com.silenker.parkingrecommendation.service.impl;

import com.silenker.parkingrecommendation.mapper.ParkingSpotMapper;
import com.silenker.parkingrecommendation.model.entity.ParkingSpot;
import com.silenker.parkingrecommendation.service.ISpotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SpotServiceImpl implements ISpotService {

    private final ParkingSpotMapper parkingSpotMapper;

    @Override
    public List<ParkingSpot> getAllSpots() {
        return parkingSpotMapper.selectAll();
    }

    @Override
    public ParkingSpot getById(String spotId) {
        return parkingSpotMapper.selectById(spotId);
    }

    @Override
    public List<ParkingSpot> getAvailableSpots() {
        return parkingSpotMapper.selectAvailable();
    }

    @Override
    public boolean updateStatus(String spotId, String status) {
        return parkingSpotMapper.updateStatusForce(spotId, status) > 0;
    }

    @Override
    public boolean updateStatusWithLock(String spotId, String status, int expectedVersion) {
        return parkingSpotMapper.updateStatus(spotId, status, expectedVersion) > 0;
    }
}
