package org.enggis.enventx.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.enggis.enventx.api.domain.enums.Role;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Service
public class JwtService {

    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_ROLE = "role";
    private static final int MIN_KEY_BYTES = 32;
    private static final long CLOCK_SKEW_SECONDS = 30;

    private final SecretKey key;
    private final Duration expiration;
    private final String issuer;

    public JwtService(JwtProperties properties) {
        var keyBytes = Decoders.BASE64.decode(properties.secret());
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("app.jwt.secret must decode to at least 32 bytes (256 bits) for HS256");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expiration = properties.expiration();
        this.issuer = properties.issuer();
    }

    public String generateToken(UserPrincipal principal) {
        var now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(principal.id().toString())
                .claim(CLAIM_EMAIL, principal.email())
                .claim(CLAIM_ROLE, principal.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Duration getExpiration() {
        return expiration;
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }

    public String extractSubject(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractEmail(String token) {
        return extractClaim(token, claims -> claims.get(CLAIM_EMAIL, String.class));
    }

    public Role extractRole(String token) {
        return Role.valueOf(extractClaim(token, claims -> claims.get(CLAIM_ROLE, String.class)));
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    public boolean isTokenValid(String token) {
        return toPrincipal(token).isPresent();
    }

    public boolean isTokenValid(String token, UserPrincipal principal) {
        return toPrincipal(token).filter(parsed -> parsed.id().equals(principal.id())).isPresent();
    }

    public Optional<UserPrincipal> toPrincipal(String token) {
        try {
            var claims = extractAllClaims(token);
            var email = claims.get(CLAIM_EMAIL, String.class);
            var role = Role.valueOf(claims.get(CLAIM_ROLE, String.class));
            return Optional.of(new UserPrincipal(UUID.fromString(claims.getSubject()), email, null, role));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            log.debug("Rejected JWT: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
