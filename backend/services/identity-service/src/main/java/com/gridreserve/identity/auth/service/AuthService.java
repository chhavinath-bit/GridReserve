package com.gridreserve.identity.auth.service;

import com.gridreserve.common.web.error.DomainException;
import com.gridreserve.identity.auth.dto.LoginRequest;
import com.gridreserve.identity.auth.dto.TokenResponse;
import com.gridreserve.identity.common.exception.IdentityErrorCode;
import com.gridreserve.identity.refresh.service.RefreshTokenService;
import com.gridreserve.identity.user.dto.RegisterRequest;
import com.gridreserve.identity.user.entity.AppUser;
import com.gridreserve.identity.user.repository.AppUserRepository;
import com.gridreserve.identity.user.service.Credentials;
import com.gridreserve.identity.user.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer tokens;
    private final RefreshTokenService refreshTokens;

    // Compared against when the email is unknown, so that case takes as long as a wrong password.
    private final String dummyHash;

    public AuthService(AppUserRepository users, UserService userService, PasswordEncoder passwordEncoder,
                       AccessTokenIssuer tokens, RefreshTokenService refreshTokens) {
        this.users = users;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.dummyHash = passwordEncoder.encode("not-a-real-password-only-for-timing");
    }

    /** Creates the account and logs it in, so the new member does not have to log in again (FR-AUTH-01, D-36). */
    @Transactional
    public Session register(RegisterRequest request) {
        return startSession(userService.register(request));
    }

    /**
     * Checks email and password and starts a session (FR-AUTH-05). Unknown email, wrong password
     * and disabled account all give the same 401 with the same message, and all run one BCrypt check,
     * so neither the answer nor its timing reveals whether an account exists (FR-AUTH-07).
     */
    @Transactional
    public Session login(LoginRequest request) {
        Optional<AppUser> user = users.findByEmail(Credentials.normalizeEmail(request.email()));
        String hash = user.map(AppUser::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = Credentials.fitsBcrypt(request.password())
                && passwordEncoder.matches(request.password(), hash);

        if (user.isEmpty() || !passwordMatches || !user.get().isEnabled()) {
            throw new DomainException(IdentityErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect.");
        }
        return startSession(user.get());
    }

    /**
     * A new access token for a valid refresh token (FR-AUTH-10). The refresh token's expiry moves
     * forward; a disabled or deleted user gets nothing (FR-AUTH-13), and the rollback undoes the move.
     */
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        return refreshTokens.use(refreshToken)
                .flatMap(users::findById)
                .filter(AppUser::isEnabled)
                .map(tokens::issue)
                .orElseThrow(() -> new DomainException(IdentityErrorCode.REFRESH_TOKEN_INVALID,
                        "Your session has ended. Log in again."));
    }

    /** Ends this device's session (FR-AUTH-11); other devices stay logged in. */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.delete(refreshToken);
    }

    // The one way a session starts: login, register, and (M1.3d) sign-in with Google or GitHub.
    private Session startSession(AppUser user) {
        return new Session(tokens.issue(user), refreshTokens.create(user.getId()));
    }
}
