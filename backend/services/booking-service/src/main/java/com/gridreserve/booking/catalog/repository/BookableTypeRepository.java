package com.gridreserve.booking.catalog.repository;

import com.gridreserve.booking.catalog.entity.BookableType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookableTypeRepository extends JpaRepository<BookableType, Long> {
}
