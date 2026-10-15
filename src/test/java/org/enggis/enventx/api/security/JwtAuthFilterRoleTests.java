package org.enggis.enventx.api.security;

import org.enggis.enventx.api.domain.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtAuthFilterRoleTests {

    private final JwtService jwt = new JwtService(new JwtProperties(
            "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=", Duration.ofHours(1), "inventx"));
    private final UUID id = UUID.randomUUID();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void run(JwtAuthFilter filter, Role tokenRole) throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwt.generateToken(new UserPrincipal(id, "u@enggis.com", null, tokenRole)));
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    @Test
    void roleComesFromTheResolverNotFromTheTokenClaim() throws Exception {
        run(new JwtAuthFilter(jwt, userId -> Optional.of(Role.EMPLOYEE)), Role.ADMIN);

        var principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertEquals(Role.EMPLOYEE, principal.role());
        assertEquals(id, principal.id());
    }

    @Test
    void deletedUsersAreNotAuthenticated() throws Exception {
        run(new JwtAuthFilter(jwt, userId -> Optional.empty()), Role.ADMIN);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void withoutAResolverTheSignedClaimIsUsed() throws Exception {
        run(new JwtAuthFilter(jwt), Role.REVIEWER);

        var principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertEquals(Role.REVIEWER, principal.role());
    }
}