package com.gridreserve.booking.booking.repository;

import com.gridreserve.booking.booking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {
}
