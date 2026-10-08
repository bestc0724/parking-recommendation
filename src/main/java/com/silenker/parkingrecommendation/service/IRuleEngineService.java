package com.silenker.parkingrecommendation.service;

import com.silenker.parkingrecommendation.model.dto.ParsedConditionsDTO;
import com.silenker.parkingrecommendation.model.entity.ParkingSpot;
import com.silenker.parkingrecommendation.model.entity.User;

import java.util.List;

public interface IRuleEngineService {
    List<ParkingSpot> filter(List<ParkingSpot> candidates, ParsedConditionsDTO conditions, User user);
}
