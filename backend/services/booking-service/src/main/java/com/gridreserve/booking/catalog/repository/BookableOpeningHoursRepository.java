package com.gridreserve.booking.catalog.repository;

import com.gridreserve.booking.catalog.entity.BookableOpeningHours;
import com.gridreserve.booking.catalog.entity.BookableOpeningHoursId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookableOpeningHoursRepository extends JpaRepository<BookableOpeningHours, BookableOpeningHoursId> {
}
