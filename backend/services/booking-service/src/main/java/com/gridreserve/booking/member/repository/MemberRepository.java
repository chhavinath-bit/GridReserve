package com.gridreserve.booking.member.repository;

import com.gridreserve.booking.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
}
