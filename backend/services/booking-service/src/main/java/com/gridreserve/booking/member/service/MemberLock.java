package com.gridreserve.booking.member.service;

import com.gridreserve.booking.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serializes one member's booking changes: while a transaction holds this lock, another hold by the
 * same member waits. That is what lets the per-member limit (BR-08, M1.7) count and insert without a
 * race (CONC-05). Third in the lock order, after venue and bookable (CLAUDE.md §8.3).
 */
@Service
public class MemberLock {

    private final MemberRepository members;

    public MemberLock(MemberRepository members) {
        this.members = members;
    }

    /** Must run inside the caller's transaction: the lock is released when it ends. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(long memberId) {
        members.lockForUpdate(memberId).orElseThrow(() ->
                new IllegalStateException("Member row missing; MemberProvisioner.ensure must run first"));
    }
}
