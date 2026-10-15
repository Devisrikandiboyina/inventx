package org.enggis.enventx.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Not a Spring bean on purpose: it is instantiated by {@link SecurityConfig} so Boot does not
 * additionally register it as a plain servlet filter.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final RoleResolver roleResolver;
    private final WebAuthenticationDetailsSource detailsSource = new WebAuthenticationDetailsSource();

    public JwtAuthFilter(JwtService jwtService) {
        this(jwtService, null);
    }

    /** With a resolver the role always comes from the database; the token's role claim is not trusted. */
    public JwtAuthFilter(JwtService jwtService, RoleResolver roleResolver) {
        this.jwtService = jwtService;
        this.roleResolver = roleResolver;
    }

    private java.util.Optional<UserPrincipal> authoritative(UserPrincipal fromToken) {
        if (roleResolver == null) {
            return java.util.Optional.of(fromToken);
        }
        return roleResolver.currentRole(fromToken.id())
                .map(role -> new UserPrincipal(fromToken.id(), fromToken.email(), fromToken.passwordHash(), role));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null
                && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            var token = header.substring(BEARER_PREFIX.length()).strip();
            jwtService.toPrincipal(token).flatMap(this::authoritative).ifPresent(principal -> {
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities());
                authentication.setDetails(detailsSource.buildDetails(request));
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            });
        }
        chain.doFilter(request, response);
    }
}
