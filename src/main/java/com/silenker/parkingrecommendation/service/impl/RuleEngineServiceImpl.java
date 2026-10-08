package com.silenker.parkingrecommendation.service.impl;

import com.silenker.parkingrecommendation.core.rule.HardConditionFilter;
import com.silenker.parkingrecommendation.model.dto.ParsedConditionsDTO;
import com.silenker.parkingrecommendation.model.entity.ParkingSpot;
import com.silenker.parkingrecommendation.model.entity.User;
import com.silenker.parkingrecommendation.service.IRuleEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEngineServiceImpl implements IRuleEngineService {

    private final HardConditionFilter hardConditionFilter;

    @Override
    public List<ParkingSpot> filter(List<ParkingSpot> candidates, ParsedConditionsDTO conditions, User user) {
        HardConditionFilter.FilterResult result = hardConditionFilter.filter(candidates, conditions, user);
        // 打印过滤日志
        result.getFilterLogs().stream()
                .filter(l -> !l.isPassed())
                .forEach(l -> log.debug("Filtered {}: {} (expected={}, actual={})",
                        l.getSpotId(), l.getReason(), l.getExpectedValue(), l.getActualValue()));
        return result.getPassed();
    }
}
