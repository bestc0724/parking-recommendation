package com.silenker.parkingrecommendation.mapper;

import com.silenker.parkingrecommendation.model.entity.Reservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ReservationMapper {

    Reservation selectById(@Param("reservationId") String reservationId);

    List<Reservation> selectByUserId(@Param("userId") String userId);

    List<Reservation> selectBySpotId(@Param("spotId") String spotId);

    List<Reservation> selectPending();

    int insert(Reservation reservation);

    int updateStatus(@Param("reservationId") String reservationId, @Param("status") String status);

    Reservation selectActiveBySpotId(@Param("spotId") String spotId);

    List<Reservation> selectExpired();

    int updateStatusAndClearExpiry(@Param("reservationId") String reservationId,
                                   @Param("status") String status);
}
