package com.silenker.parkingrecommendation.service;

import com.silenker.parkingrecommendation.model.dto.ParsedConditionsDTO;
import com.silenker.parkingrecommendation.model.dto.RecommendResultDTO;
import com.silenker.parkingrecommendation.model.entity.ParkingSpot;

import java.util.List;

public interface IRecommenderService {
    List<RecommendResultDTO> recommend(List<ParkingSpot> candidates, ParsedConditionsDTO conditions);
}
