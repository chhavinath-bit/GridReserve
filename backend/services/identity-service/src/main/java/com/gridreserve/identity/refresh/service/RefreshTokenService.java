package com.gridreserve.identity.refresh.service;

import com.gridreserve.identity.refresh.entity.RefreshToken;
import com.gridreserve.identity.refresh.repository.RefreshTokenRepository;
import com.gridreserve.identity.security.RefreshTokenProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Refresh tokens, one per login (FR-AUTH-09..11, D-37). The token value never changes; each use
 * moves its expiry to now + TTL, so it ends only after a full TTL without use, or at logout.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository tokens;
    private final RefreshTokenProperties properties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository tokens, RefreshTokenProperties properties, Clock clock) {
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
    }

    /** Starts a session for the user and returns the token for the cookie. Also clears out expired sessions. */
    @Transactional
    public String create(long userId) {
        Instant now = clock.instant();
        tokens.deleteExpired(now);
        String token = RefreshTokens.newToken();
        tokens.save(new RefreshToken(userId, RefreshTokens.hash(token), now, now.plus(properties.ttl())));
        return token;
    }

    /** The user ID if the token is valid (and its expiry is moved forward), otherwise empty. */
    @Transactional
    public Optional<Long> use(String token) {
        Instant now = clock.instant();
        String hash = RefreshTokens.hash(token);
        if (tokens.extend(hash, now, now.plus(properties.ttl())) == 0) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(hash).map(RefreshToken::getUserId);
    }

    /** Ends the session of this token. Nothing happens if it is already gone. */
    @Transactional
    public void delete(String token) {
        tokens.deleteByTokenHash(RefreshTokens.hash(token));
    }
}
