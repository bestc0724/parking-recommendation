package com.silenker.parkingrecommendation.service.impl;

import com.silenker.parkingrecommendation.core.rule.SoftPreferenceSorter;
import com.silenker.parkingrecommendation.model.dto.ParsedConditionsDTO;
import com.silenker.parkingrecommendation.model.dto.RecommendResultDTO;
import com.silenker.parkingrecommendation.model.entity.ParkingSpot;
import com.silenker.parkingrecommendation.service.IRecommenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommenderServiceImpl implements IRecommenderService {

    private final SoftPreferenceSorter softPreferenceSorter;

    @Override
    public List<RecommendResultDTO> recommend(List<ParkingSpot> candidates, ParsedConditionsDTO conditions) {
        return softPreferenceSorter.sort(candidates, conditions);
    }
}
