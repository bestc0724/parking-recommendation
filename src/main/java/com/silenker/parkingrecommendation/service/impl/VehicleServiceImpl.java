package com.silenker.parkingrecommendation.service.impl;

import com.silenker.parkingrecommendation.mapper.VehicleMapper;
import com.silenker.parkingrecommendation.model.entity.Vehicle;
import com.silenker.parkingrecommendation.service.IVehicleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements IVehicleService {

    private final VehicleMapper vehicleMapper;

    @Override
    public Vehicle addVehicle(String userId, String plateNumber, String model,
                              BigDecimal vehicleHeight, Boolean isEv) {
        if (userId == null || userId.isBlank()) {
            throw new RuntimeException("用户ID不能为空");
        }
        if (plateNumber == null || plateNumber.isBlank()) {
            throw new RuntimeException("车牌号不能为空");
        }

        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId("V" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        vehicle.setUserId(userId);
        vehicle.setPlateNumber(plateNumber);
        vehicle.setModel(model);
        vehicle.setVehicleHeight(vehicleHeight);
        vehicle.setIsEv(isEv != null && isEv);
        vehicleMapper.insert(vehicle);

        log.info("Vehicle added: {} plate={} height={}m ev={}",
                vehicle.getVehicleId(), plateNumber, vehicleHeight, vehicle.getIsEv());
        return vehicle;
    }

    @Override
    public List<Vehicle> getUserVehicles(String userId) {
        return vehicleMapper.selectByUserId(userId);
    }

    @Override
    public Vehicle getById(String vehicleId) {
        return vehicleMapper.selectById(vehicleId);
    }
}
