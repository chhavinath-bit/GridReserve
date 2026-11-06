package com.gridreserve.booking.catalog.repository;

import com.gridreserve.booking.catalog.entity.Bookable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookableRepository extends JpaRepository<Bookable, Long> {
}
