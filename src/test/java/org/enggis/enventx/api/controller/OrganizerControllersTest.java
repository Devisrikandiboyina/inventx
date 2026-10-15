package org.enggis.enventx.api.controller;

import org.enggis.enventx.api.domain.enums.AnnouncementState;
import org.enggis.enventx.api.domain.enums.DeliveryStatus;
import org.enggis.enventx.api.domain.enums.Role;
import org.enggis.enventx.api.dto.OrganizerAnnouncementDto;
import org.enggis.enventx.api.dto.OrganizerGuidelineDto;
import org.enggis.enventx.api.dto.PagedResponse;
import org.enggis.enventx.api.dto.WhatsAppAttemptDto;
import org.enggis.enventx.api.dto.WhatsAppIntegrationStatusDto;
import org.enggis.enventx.api.security.JwtService;
import org.enggis.enventx.api.security.SecurityConfig;
import org.enggis.enventx.api.security.UserPrincipal;
import org.enggis.enventx.api.service.IdeaFileService;
import org.enggis.enventx.api.service.IdeaService;
import org.enggis.enventx.api.service.OrganizerAnnouncementService;
import org.enggis.enventx.api.service.OrganizerDashboardService;
import org.enggis.enventx.api.service.OrganizerGuidelineService;
import org.enggis.enventx.api.service.OrganizerSettingsService;
import org.enggis.enventx.api.service.OrganizerStageService;
import org.enggis.enventx.api.service.OrganizerUserService;
import org.enggis.enventx.api.service.WhatsAppService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({OrganizerAdminController.class, OrganizerContentController.class,
        OrganizerWhatsAppController.class, OrganizerIdeaController.class})
@Import({OrganizerAdminController.class, OrganizerContentController.class, OrganizerWhatsAppController.class,
        OrganizerIdeaController.class, SecurityConfig.class, JwtService.class, GlobalExceptionHandler.class,
        OrganizerControllersTest.Config.class})
@TestPropertySource(properties = {
        "app.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        "app.cors.allowed-origins=http://localhost:3000"
})
class OrganizerControllersTest {

    @SpringBootConfiguration
    static class Config {
    }

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean IdeaService ideaService;
    @MockitoBean IdeaFileService files;
    @MockitoBean OrganizerDashboardService dashboard;
    @MockitoBean OrganizerStageService stages;
    @MockitoBean OrganizerGuidelineService guidelines;
    @MockitoBean OrganizerAnnouncementService announcements;
    @MockitoBean OrganizerUserService userService;
    @MockitoBean OrganizerSettingsService settings;
    @MockitoBean WhatsAppService whatsApp;

    private final UUID userId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    private String bearer(Role role) {
        return "Bearer " + jwt.generateToken(new UserPrincipal(userId, "u@enggis.com", "x", role));
    }

    @Test
    void everyOrganizerEndpointRejectsEmployeesAndAnonymousCallers() throws Exception {
        mvc.perform(get("/api/organizer/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/organizer/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(Role.EMPLOYEE)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/organizer/users").header(HttpHeaders.AUTHORIZATION, bearer(Role.REVIEWER)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/organizer/settings").header(HttpHeaders.AUTHORIZATION, bearer(Role.EMPLOYEE))
                .contentType(MediaType.APPLICATION_JSON).content("{\"judgingCriteria\":{\"a\":100}}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(dashboard, userService, settings);
    }

    @Test
    void ideaListForwardsFilters() throws Exception {
        when(ideaService.listAllForOrganizer(eq(userId), eq("robot"), any(), any(), any(), eq("DRAFT"),
                eq(targetId), eq("popular"), eq(1), eq(5)))
                .thenReturn(new PagedResponse<>(List.of(), 1, 5, 0, 0));

        mvc.perform(get("/api/organizer/ideas").header(HttpHeaders.AUTHORIZATION, bearer(Role.ORGANIZER))
                        .param("q", "robot").param("status", "DRAFT").param("ownerId", targetId.toString())
                        .param("sort", "popular").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void ideaPatchValidatesCategoryAndTags() throws Exception {
        mvc.perform(patch("/api/organizer/ideas/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.ORGANIZER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"NOPE\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/organizer/ideas/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.ORGANIZER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tags\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\",\"h\",\"i\",\"j\",\"k\"]}"))
                .andExpect(status().isBadRequest());
        verify(ideaService, never()).organizerUpdateIdea(any(), any(), any());
    }

    @Test
    void stagePatchForwardsCodeAndActor() throws Exception {
        mvc.perform(patch("/api/organizer/stages/SCREENING").header(HttpHeaders.AUTHORIZATION, bearer(Role.ORGANIZER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"2026-03-01\",\"endDate\":\"2026-03-31\",\"confirmation\":true}"))
                .andExpect(status().isOk());
        verify(stages).update(eq("SCREENING"), eq(userId), any());

        mvc.perform(patch("/api/organizer/stages/SCREENING").header(HttpHeaders.AUTHORIZATION, bearer(Role.ORGANIZER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmation\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void roleChangeUsesCallerIdAndPropagatesForbidden() throws Exception {
        when(userService.changeRole(targetId, userId, Role.REVIEWER))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "CANNOT_CHANGE_OWN_ROLE"));

        mvc.perform(patch("/api/organizer/users/" + targetId + "/role")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.ORGANIZER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"REVIEWER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CANNOT_CHANGE_OWN_ROLE"));

        mvc.perform(patch("/api/organizer/users/" + targetId + "/role")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.ORGANIZER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"KING\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void settingsRequireNonEmptyBoundedWeights() throws Exception {
        for (var body : List.of("{}", "{\"judgingCriteria\":{}}", "{\"judgingCriteria\":{\"a\":101}}",
                "{\"judgingCriteria\":{\"bad key!\":100}}")) {
            mvc.perform(put("/api/organizer/settings").header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(settings);

        mvc.perform(put("/api/organizer/settings").header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"judgingCriteria\":{\"a\":60,\"b\":40}}"))
                .andExpect(status().isOk());
        verify(settings).update(eq(userId), any());
    }

    @Test
    void guidelineEndpoints() throws Exception {
        var dto = new OrganizerGuidelineDto(UUID.randomUUID(), "T", "C", 1, false, null, false, Instant.now(), Instant.now());
        when(guidelines.create(any())).thenReturn(dto);
        when(guidelines.get(dto.id())).thenReturn(dto);

        mvc.perform(post("/api/organizer/guidelines").header(HttpHeaders.AUTHORIZATION, bearer(Role.ORGANIZER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"T\",\"content\":\"C\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/organizer/guidelines").header(HttpHeaders.AUTHORIZATION, bearer(Role.ORGANIZER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\" \",\"content\":\"C\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/organizer/guidelines/" + dto.id()).header(HttpHeaders.AUTHORIZATION, bearer(Role.ORGANIZER)))
                .andExpect(status().isNoContent());
        verify(guidelines).delete(dto.id());

        mvc.perform(multipart("/api/organizer/guidelines/" + dto.id() + "/file")
                        .file(new MockMultipartFile("file", "g.pdf", "application/pdf", "%PDF-1".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.ORGANIZER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("T"));
        verify(files).replaceGuidelineFile(eq(dto.id()), any());
    }

    @Test
    void announcementEndpoints() throws Exception {
        var id = UUID.randomUUID();
        var dto = new OrganizerAnnouncementDto(id, "T", "C", AnnouncementState.PUBLISHED, Instant.now(), false,
                Instant.now(), Instant.now());
        when(announcements.changeState(id, userId, AnnouncementState.PUBLISHED)).thenReturn(dto);

        mvc.perform(post("/api/organizer/announcements/" + id + "/state")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.ORGANIZER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"state\":\"PUBLISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PUBLISHED"));
        mvc.perform(post("/api/organizer/announcements/" + id + "/state")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.ORGANIZER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"state\":\"DELETED\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/organizer/announcements").header(HttpHeaders.AUTHORIZATION, bearer(Role.ORGANIZER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\",\"content\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void whatsAppEndpoints() throws Exception {
        var id = UUID.randomUUID();
        when(whatsApp.status()).thenReturn(new WhatsAppIntegrationStatusDto(
                false, "WHATSAPP_CLOUD_API", false, "TEMPLATE_TO_OPTED_IN_NUMBERS", 0, null, null,
                List.of("whatsapp.access-token"), null));
        when(whatsApp.send(id, userId)).thenReturn(new WhatsAppAttemptDto(
                UUID.randomUUID(), id, DeliveryStatus.NOT_CONFIGURED, null, "not configured", 0, 0, userId, Instant.now()));

        mvc.perform(get("/api/organizer/integrations/whatsapp").header(HttpHeaders.AUTHORIZATION, bearer(Role.ORGANIZER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supportsGroupDelivery").value(false))
                .andExpect(jsonPath("$.configured").value(false));
        mvc.perform(post("/api/organizer/announcements/" + id + "/whatsapp-notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.ORGANIZER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_CONFIGURED"));
    }
}
