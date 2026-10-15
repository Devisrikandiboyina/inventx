package org.enggis.enventx.api.service;

import lombok.RequiredArgsConstructor;
import org.enggis.enventx.api.domain.entity.User;
import org.enggis.enventx.api.domain.enums.Role;
import org.enggis.enventx.api.dto.AuthResponse;
import org.enggis.enventx.api.dto.LoginRequest;
import org.enggis.enventx.api.dto.RegisterRequest;
import org.enggis.enventx.api.dto.UserDto;
import org.enggis.enventx.api.repository.UserRepository;
import org.enggis.enventx.api.security.JwtService;
import org.enggis.enventx.api.security.UserPrincipal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        var email = normalize(request.email());
        var company = companyFor(email)
                .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "EMAIL_DOMAIN_NOT_ALLOWED"));
        if (request.password() == null || request.password().isBlank()) {
            throw error(HttpStatus.BAD_REQUEST, "PASSWORD_REQUIRED");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw error(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_LONG");
        }
        if (users.existsByEmail(email)) {
            throw error(HttpStatus.CONFLICT, "EMAIL_TAKEN");
        }

        var user = new User();
        user.setName(request.name().strip());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setCompany(company);
        user.setRole(Role.EMPLOYEE);

        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // lost a race with a concurrent registration of the same email; the unique index is the arbiter
            throw error(HttpStatus.CONFLICT, "EMAIL_TAKEN");
        }
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        var user = users.findByEmail(normalize(request.email())).orElse(null);
        // Always run one BCrypt comparison so response time does not reveal whether the email exists
        var hash = user != null ? user.getPasswordHash() : passwordEncoder.encode(request.password());
        var matches = request.password().getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES
                && passwordEncoder.matches(request.password(), hash);
        if (user == null || !matches) {
            throw error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }
        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        return new AuthResponse(UserDto.from(user), jwtService.generateToken(UserPrincipal.from(user)));
    }

    private static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    private static Optional<String> companyFor(String email) {
        var at = email.lastIndexOf('@');
        if (at <= 0) {
            return Optional.empty();
        }
        return switch (email.substring(at + 1)) {
            case "enggis.com" -> Optional.of("ENGGIS");
            case "nexsys.co.in" -> Optional.of("NEXSYS");
            case "stability.co" -> Optional.of("STABILITY");
            default -> Optional.empty();
        };
    }

    private static ResponseStatusException error(HttpStatus status, String code) {
        return new ResponseStatusException(status, code);
    }
}
