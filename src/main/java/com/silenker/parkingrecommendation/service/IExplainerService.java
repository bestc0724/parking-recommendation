package com.silenker.parkingrecommendation.service;

import com.silenker.parkingrecommendation.model.dto.ParsedConditionsDTO;
import com.silenker.parkingrecommendation.model.dto.RecommendResultDTO;

public interface IExplainerService {
    String generateExplanation(RecommendResultDTO recommendation, ParsedConditionsDTO conditions, String originalQuery);
}
