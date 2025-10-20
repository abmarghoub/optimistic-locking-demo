package com.example.service;

import com.example.model.Reservation;
import java.util.Optional;

public interface ReservationService {

    boolean hasOverlap(Reservation reservation);
    Reservation save(Reservation reservation);
    Optional<Reservation> findById(Long id);
    void update(Reservation reservation);
    void delete(Reservation reservation);
}