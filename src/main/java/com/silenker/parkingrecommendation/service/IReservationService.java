package com.silenker.parkingrecommendation.service;

import com.silenker.parkingrecommendation.model.entity.Reservation;

import java.util.List;

public interface IReservationService {
    Reservation reserve(String userId, String spotId);
    boolean confirmArrival(String reservationId);
    boolean cancel(String reservationId);
    List<Reservation> getUserReservations(String userId);
    List<Reservation> getPendingReservations();
}
