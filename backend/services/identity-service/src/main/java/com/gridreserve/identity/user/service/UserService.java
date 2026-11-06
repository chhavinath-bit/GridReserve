package com.gridreserve.identity.user.service;

import com.gridreserve.common.web.error.CommonErrorCode;
import com.gridreserve.common.web.error.DomainException;
import com.gridreserve.identity.common.exception.ConstraintViolations;
import com.gridreserve.identity.user.dto.RegisterRequest;
import com.gridreserve.identity.user.dto.UserResponse;
import com.gridreserve.identity.user.entity.AppUser;
import com.gridreserve.identity.user.entity.Role;
import com.gridreserve.identity.user.mapper.UserMapper;
import com.gridreserve.identity.user.repository.AppUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class UserService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserService(AppUserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * Creates a MEMBER account (FR-AUTH-01, FR-AUTH-04: never an ADMIN). A duplicate email is not
     * checked first: the unique constraint decides, so two simultaneous registrations of the same
     * address give exactly one account and one {@code 409 EMAIL_TAKEN}.
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (!Credentials.fitsBcrypt(request.password())) {
            throw new DomainException(CommonErrorCode.VALIDATION_FAILED,
                    "The password is too long. Use at most 72 bytes (fewer characters with accents or emoji).");
        }
        AppUser user = new AppUser(Credentials.normalizeEmail(request.email()),
                passwordEncoder.encode(request.password()), request.displayName().trim(), Role.MEMBER,
                clock.instant());
        try {
            // Flush now, so a constraint violation surfaces here and not at commit, outside this try.
            return UserMapper.toResponse(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException ex) {
            throw ConstraintViolations.translate(ex);
        }
    }

    /** The current user (FR-AUTH-08). A valid token for a user that no longer exists counts as not signed in. */
    @Transactional(readOnly = true)
    public UserResponse get(long userId) {
        return users.findById(userId)
                .map(UserMapper::toResponse)
                .orElseThrow(() -> new DomainException(CommonErrorCode.UNAUTHENTICATED, "Sign in to continue."));
    }
}
