package com.silenker.parkingrecommendation.service;

import com.silenker.parkingrecommendation.model.dto.ParsedConditionsDTO;

public interface INluService {
    ParsedConditionsDTO parse(String query);
}
