package com.silenker.parkingrecommendation.service;

import com.silenker.parkingrecommendation.model.entity.Vehicle;

import java.math.BigDecimal;
import java.util.List;

/**
 * 车辆档案服务 — 支撑"无感推荐"
 */
public interface IVehicleService {

    Vehicle addVehicle(String userId, String plateNumber, String model,
                       BigDecimal vehicleHeight, Boolean isEv);

    List<Vehicle> getUserVehicles(String userId);

    Vehicle getById(String vehicleId);
}
