package com.gridreserve.booking.member.service;

import com.gridreserve.booking.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Makes sure booking-service has its local row for an identity user (ARCHITECTURE §5.3, "Why a local member table?") before
 * anything needs to lock it. The ID comes from the verified access token ({@code sub}), so no call
 * to identity-service is needed. Hold creation (M1.5) calls this first, inside its own transaction,
 * and then locks the row ({@code FOR UPDATE}) to count the member's bookings (BR-08).
 */
@Service
public class MemberProvisioner {

    private final MemberRepository members;
    private final Clock clock;

    public MemberProvisioner(MemberRepository members, Clock clock) {
        this.members = members;
        this.clock = clock;
    }

    /** Returns true when this call created the row. Joins the caller's transaction if there is one. */
    @Transactional
    public boolean ensure(long memberId) {
        return members.insertIfAbsent(memberId, clock.instant()) == 1;
    }
}
