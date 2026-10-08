package com.silenker.parkingrecommendation.service.impl;

import com.silenker.parkingrecommendation.core.nlu.NluParser;
import com.silenker.parkingrecommendation.model.dto.ParsedConditionsDTO;
import com.silenker.parkingrecommendation.service.INluService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NluServiceImpl implements INluService {

    private final NluParser nluParser;

    @Override
    public ParsedConditionsDTO parse(String query) {
        return nluParser.parse(query);
    }
}
