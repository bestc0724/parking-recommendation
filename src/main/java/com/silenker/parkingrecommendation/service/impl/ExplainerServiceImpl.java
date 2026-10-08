package com.silenker.parkingrecommendation.service.impl;

import com.silenker.parkingrecommendation.core.explainer.ReasonGenerator;
import com.silenker.parkingrecommendation.model.dto.ParsedConditionsDTO;
import com.silenker.parkingrecommendation.model.dto.RecommendResultDTO;
import com.silenker.parkingrecommendation.service.IExplainerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExplainerServiceImpl implements IExplainerService {

    private final ReasonGenerator reasonGenerator;

    @Override
    public String generateExplanation(RecommendResultDTO recommendation,
                                       ParsedConditionsDTO conditions,
                                       String originalQuery) {
        return reasonGenerator.generate(recommendation, conditions, originalQuery);
    }
}
