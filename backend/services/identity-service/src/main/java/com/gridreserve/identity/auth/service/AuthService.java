package com.gridreserve.identity.auth.service;

import com.gridreserve.common.web.error.DomainException;
import com.gridreserve.identity.auth.dto.LoginRequest;
import com.gridreserve.identity.auth.dto.TokenResponse;
import com.gridreserve.identity.common.exception.IdentityErrorCode;
import com.gridreserve.identity.user.entity.AppUser;
import com.gridreserve.identity.user.repository.AppUserRepository;
import com.gridreserve.identity.user.service.Credentials;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer tokens;

    // Compared against when the email is unknown, so that case takes as long as a wrong password.
    private final String dummyHash;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, AccessTokenIssuer tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.dummyHash = passwordEncoder.encode("not-a-real-password-only-for-timing");
    }

    /**
     * Checks email and password and issues an access token (FR-AUTH-05). Unknown email, wrong password
     * and disabled account all give the same 401 with the same message, and all run one BCrypt check,
     * so neither the answer nor its timing reveals whether an account exists (FR-AUTH-07).
     */
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Optional<AppUser> user = users.findByEmail(Credentials.normalizeEmail(request.email()));
        String hash = user.map(AppUser::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = Credentials.fitsBcrypt(request.password())
                && passwordEncoder.matches(request.password(), hash);

        if (user.isEmpty() || !passwordMatches || !user.get().isEnabled()) {
            throw new DomainException(IdentityErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect.");
        }
        return tokens.issue(user.get());
    }
}
