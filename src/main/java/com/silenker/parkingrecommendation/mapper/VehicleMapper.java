package com.silenker.parkingrecommendation.mapper;

import com.silenker.parkingrecommendation.model.entity.Vehicle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface VehicleMapper {

    Vehicle selectById(@Param("vehicleId") String vehicleId);

    List<Vehicle> selectByUserId(@Param("userId") String userId);

    int insert(Vehicle vehicle);
}
