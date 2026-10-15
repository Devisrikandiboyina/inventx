package org.enggis.enventx.api.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.Filter;
import org.enggis.enventx.api.domain.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(classes = {SecurityConfig.class, JwtService.class, SecurityConfigTests.WebConfig.class})
@TestPropertySource(properties = {
        "app.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        "app.cors.allowed-origins=http://localhost:3000"
})
class SecurityConfigTests {

    @Configuration
    @EnableWebMvc
    static class WebConfig {

        @RestController
        static class ProbeController {
            @GetMapping("/api/auth/login")
            String login() {
                return "public";
            }

            @GetMapping("/api/ideas")
            String ideas() {
                return "ideas";
            }

            @GetMapping("/api/organizer/dashboard")
            String organizer() {
                return "organizer";
            }
        }
    }

    @Autowired
    WebApplicationContext context;
    @Autowired
    JwtService jwtService;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    private String bearer(Role role) {
        return "Bearer " + jwtService.generateToken(new UserPrincipal(UUID.randomUUID(), "u@enggis.com", null, role));
    }

    @Test
    void publicEndpointNeedsNoToken() throws Exception {
        mvc().perform(get("/api/auth/login")).andExpect(status().isOk());
    }

    @Test
    void protectedEndpointWithoutTokenIs401Json() throws Exception {
        mvc().perform(get("/api/ideas"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, org.hamcrest.Matchers.startsWith("application/json")));
    }

    @Test
    void protectedEndpointWithValidTokenIsOk() throws Exception {
        mvc().perform(get("/api/ideas").header(HttpHeaders.AUTHORIZATION, bearer(Role.EMPLOYEE)))
                .andExpect(status().isOk());
    }

    @Test
    void garbageAndTamperedTokensAreRejected() throws Exception {
        mvc().perform(get("/api/ideas").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
        var tampered = bearer(Role.EMPLOYEE).replace(".", "x.");
        mvc().perform(get("/api/ideas").header(HttpHeaders.AUTHORIZATION, tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void organizerEndpointEnforcesRoles() throws Exception {
        mvc().perform(get("/api/organizer/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(Role.EMPLOYEE)))
                .andExpect(status().isForbidden());
        mvc().perform(get("/api/organizer/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(Role.REVIEWER)))
                .andExpect(status().isForbidden());
        mvc().perform(get("/api/organizer/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(Role.ORGANIZER)))
                .andExpect(status().isOk());
        mvc().perform(get("/api/organizer/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void preflightFromAllowedOriginSucceedsWithCredentials() throws Exception {
        mvc().perform(options("/api/ideas")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void preflightFromUnknownOriginIsRejected() throws Exception {
        mvc().perform(options("/api/ideas")
                        .header(HttpHeaders.ORIGIN, "http://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void jwtServiceRoundTripsAndRejectsExpiredAndForeignTokens() {
        var principal = new UserPrincipal(UUID.randomUUID(), "u@enggis.com", null, Role.REVIEWER);
        var token = jwtService.generateToken(principal);
        assertTrue(jwtService.isTokenValid(token, principal));
        assertEquals("u@enggis.com", jwtService.extractEmail(token));
        assertEquals(Role.REVIEWER, jwtService.extractRole(token));
        assertFalse(jwtService.isTokenExpired(token));

        var foreignKey = Keys.hmacShaKeyFor("a-completely-different-32-byte-key!!".getBytes());
        var foreign = Jwts.builder().issuer("inventx").subject(principal.id().toString())
                .claim("email", "u@enggis.com").claim("role", "ADMIN").signWith(foreignKey).compact();
        assertFalse(jwtService.isTokenValid(foreign));

        var key = Keys.hmacShaKeyFor(java.util.Base64.getDecoder()
                .decode("MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="));
        var expired = Jwts.builder().issuer("inventx").subject(principal.id().toString())
                .claim("email", "u@enggis.com").claim("role", "REVIEWER")
                .expiration(new Date(System.currentTimeMillis() - 120_000)).signWith(key).compact();
        assertFalse(jwtService.isTokenValid(expired));
        assertTrue(jwtService.isTokenExpired(expired));
    }
}
