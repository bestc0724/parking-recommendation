package com.silenker.parkingrecommendation.mapper;

import com.silenker.parkingrecommendation.model.entity.ParkingSpot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ParkingSpotMapper {

    List<ParkingSpot> selectAll();

    ParkingSpot selectById(@Param("spotId") String spotId);

    List<ParkingSpot> selectByStatus(@Param("status") String status);

    List<ParkingSpot> selectAvailable();

    int updateStatus(@Param("spotId") String spotId, @Param("status") String status,
                     @Param("expectedVersion") int expectedVersion);

    int updateStatusForce(@Param("spotId") String spotId, @Param("status") String status);
}
