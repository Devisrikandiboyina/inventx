package org.enggis.enventx.api.service;

import org.enggis.enventx.api.domain.enums.Role;
import org.enggis.enventx.api.dto.LoginRequest;
import org.enggis.enventx.api.dto.RegisterRequest;
import org.enggis.enventx.api.repository.UserRepository;
import org.enggis.enventx.api.security.JwtProperties;
import org.enggis.enventx.api.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(AuthService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthServiceTests {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("org.enggis.enventx.api.domain.entity")
    @EnableJpaRepositories("org.enggis.enventx.api.repository")
    static class TestApp {
    }

    @TestConfiguration
    static class Beans {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }

        @Bean
        JwtService jwtService() {
            return new JwtService(new JwtProperties(
                    "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=", Duration.ofHours(1), "inventx"));
        }
    }

    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    @BeforeEach
    void clean() {
        users.deleteAll();
    }

    private ResponseStatusException registerFails(String email, String password) {
        return assertThrows(ResponseStatusException.class,
                () -> auth.register(new RegisterRequest("Name", email, password)));
    }

    @Test
    void registerDerivesCompanyForcesRoleAndHashesPassword() {
        var expected = java.util.Map.of(
                "a@enggis.com", "ENGGIS", "b@NexSys.CO.IN", "NEXSYS", "c@stability.co", "STABILITY");
        expected.forEach((email, company) -> {
            var response = auth.register(new RegisterRequest("  Some Body ", email, "password123"));
            var saved = users.findByEmail(email.toLowerCase()).orElseThrow();
            assertEquals(company, saved.getCompany());
            assertEquals(Role.EMPLOYEE, saved.getRole());
            assertEquals("Some Body", saved.getName());
            assertNotEquals("password123", saved.getPasswordHash());
            assertTrue(encoder.matches("password123", saved.getPasswordHash()));
            assertEquals(saved.getId().toString(), jwt.extractSubject(response.accessToken()));
            assertEquals(Role.EMPLOYEE, jwt.extractRole(response.accessToken()));
        });
    }

    @Test
    void registerRejectsDisallowedDomains() {
        for (var email : new String[]{"a@gmail.com", "a@enggis.com.evil.io", "a@sub.enggis.com", "@enggis.com", "nope"}) {
            var e = registerFails(email, "password123");
            assertEquals(400, e.getStatusCode().value(), email);
            assertEquals("EMAIL_DOMAIN_NOT_ALLOWED", e.getReason(), email);
        }
        assertEquals(0, users.count());
    }

    @Test
    void duplicateEmailIsConflictIgnoringCase() {
        auth.register(new RegisterRequest("One", "dup@enggis.com", "password123"));
        var e = registerFails("DUP@enggis.com", "password123");
        assertEquals(409, e.getStatusCode().value());
        assertEquals("EMAIL_TAKEN", e.getReason());
        assertEquals(1, users.count());
    }

    @Test
    void passwordLongerThan72BytesIsRejectedCleanly() {
        var e = registerFails("long@enggis.com", "\u00e9".repeat(40));
        assertEquals(400, e.getStatusCode().value());
        assertEquals("PASSWORD_TOO_LONG", e.getReason());
    }

    @Test
    void loginSucceedsWithCorrectPasswordAndFailsUniformlyOtherwise() {
        auth.register(new RegisterRequest("One", "me@enggis.com", "password123"));

        var ok = auth.login(new LoginRequest("ME@enggis.com", "password123"));
        assertEquals("me@enggis.com", ok.user().email());

        var wrongPassword = assertThrows(ResponseStatusException.class,
                () -> auth.login(new LoginRequest("me@enggis.com", "wrong-password")));
        var unknownUser = assertThrows(ResponseStatusException.class,
                () -> auth.login(new LoginRequest("ghost@enggis.com", "password123")));
        assertEquals(401, wrongPassword.getStatusCode().value());
        assertEquals(wrongPassword.getReason(), unknownUser.getReason());
    }
}
