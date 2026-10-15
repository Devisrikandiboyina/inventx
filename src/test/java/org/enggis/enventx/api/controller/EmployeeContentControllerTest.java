package org.enggis.enventx.api.controller;

import org.enggis.enventx.api.domain.enums.Role;
import org.enggis.enventx.api.dto.ReadAllResultDto;
import org.enggis.enventx.api.security.JwtService;
import org.enggis.enventx.api.security.SecurityConfig;
import org.enggis.enventx.api.security.UserPrincipal;
import org.enggis.enventx.api.service.ContentService;
import org.enggis.enventx.api.service.DashboardService;
import org.enggis.enventx.api.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmployeeContentController.class)
@Import({EmployeeContentController.class, SecurityConfig.class, JwtService.class,
        GlobalExceptionHandler.class, EmployeeContentControllerTest.Config.class})
@TestPropertySource(properties = {
        "app.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        "app.cors.allowed-origins=http://localhost:3000"
})
class EmployeeContentControllerTest {

    @SpringBootConfiguration
    static class Config {
    }

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean DashboardService dashboard;
    @MockitoBean ContentService content;
    @MockitoBean NotificationService notifications;

    private final UUID userId = UUID.randomUUID();

    private String bearer() {
        return "Bearer " + jwt.generateToken(new UserPrincipal(userId, "u@enggis.com", "x", Role.EMPLOYEE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/dashboard", "/api/stages", "/api/guidelines", "/api/announcements",
            "/api/notifications", "/api/settings"})
    void everyEndpointRequiresAuthentication(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/stages", "/api/guidelines", "/api/announcements"})
    void listEndpointsReturnEmptyArraysForAuthenticatedUsers(String path) throws Exception {
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void readAllUsesTheCallersIdentity() throws Exception {
        when(notifications.markAllRead(userId)).thenReturn(new ReadAllResultDto(3));

        mvc.perform(post("/api/notifications/read-all").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(3));
        verify(notifications).markAllRead(userId);
    }
}
