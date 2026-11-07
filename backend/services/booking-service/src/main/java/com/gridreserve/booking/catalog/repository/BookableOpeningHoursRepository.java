package com.gridreserve.booking.catalog.repository;

import com.gridreserve.booking.catalog.entity.BookableOpeningHours;
import com.gridreserve.booking.catalog.entity.BookableOpeningHoursId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookableOpeningHoursRepository extends JpaRepository<BookableOpeningHours, BookableOpeningHoursId> {

    List<BookableOpeningHours> findByIdBookableId(Long bookableId);
}
