package org.enggis.enventx.api.controller;

import org.enggis.enventx.api.domain.enums.Role;
import org.enggis.enventx.api.dto.IdeaDto;
import org.enggis.enventx.api.dto.LikeStatusDto;
import org.enggis.enventx.api.dto.PagedResponse;
import org.enggis.enventx.api.dto.StageTransitionRequest;
import org.enggis.enventx.api.security.JwtService;
import org.enggis.enventx.api.security.SecurityConfig;
import org.enggis.enventx.api.security.UserPrincipal;
import org.enggis.enventx.api.service.IdeaLikeService;
import org.enggis.enventx.api.service.IdeaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({IdeaController.class, OrganizerIdeaController.class})
@Import({IdeaController.class, OrganizerIdeaController.class, SecurityConfig.class, JwtService.class, GlobalExceptionHandler.class, IdeaControllerTest.Config.class})
@TestPropertySource(properties = {
        "app.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        "app.cors.allowed-origins=http://localhost:3000"
})
class IdeaControllerTest {

    @SpringBootConfiguration
    static class Config {
    }

    private static final String VALID_IDEA = """
            {"title":"Smart arm","summary":"s","description":"d","category":"ROBOTICS",
             "targetCustomer":"c","businessImpact":"b","teamMembers":[]}""";

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean IdeaService ideaService;
    @MockitoBean IdeaLikeService likeService;

    private final UUID userId = UUID.randomUUID();
    private final UUID ideaId = UUID.randomUUID();

    private String bearer(Role role) {
        return "Bearer " + jwt.generateToken(new UserPrincipal(userId, "u@enggis.com", "x", role));
    }

    @Test
    void ideasRequireAuthentication() throws Exception {
        mvc.perform(get("/api/ideas")).andExpect(status().isUnauthorized());
    }

    @Test
    void listForwardsQueryParametersAndCallerId() throws Exception {
        when(ideaService.listIdeas(any(), any(), any(), any(), any(), any(), eq(2), eq(10)))
                .thenReturn(new PagedResponse<>(List.of(), 2, 10, 0, 0));

        mvc.perform(get("/api/ideas")
                        .param("q", "robot").param("category", "ROBOTICS").param("company", "ENGGIS")
                        .param("stage", "SCREENING").param("sort", "popular").param("page", "2").param("size", "10")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.EMPLOYEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2));

        verify(ideaService).listIdeas(userId, "robot", "ROBOTICS", "ENGGIS", "SCREENING", "popular", 2, 10);
    }

    @Test
    void createReturns201() throws Exception {
        when(ideaService.createIdea(eq(userId), any())).thenReturn(null);

        mvc.perform(post("/api/ideas").contentType(MediaType.APPLICATION_JSON).content(VALID_IDEA)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.EMPLOYEE)))
                .andExpect(status().isCreated());
    }

    @Test
    void ideaLimitIsReportedAs409WithMachineCode() throws Exception {
        when(ideaService.createIdea(eq(userId), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "IDEA_LIMIT"));

        mvc.perform(post("/api/ideas").contentType(MediaType.APPLICATION_JSON).content(VALID_IDEA)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.EMPLOYEE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEA_LIMIT"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void invalidBodyIsReportedWithFieldErrors() throws Exception {
        mvc.perform(post("/api/ideas").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_IDEA.replace("Smart arm", "ab"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.EMPLOYEE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        verify(ideaService, never()).createIdea(any(), any());
    }

    @Test
    void malformedJsonAndBadUuidAreBadRequests() throws Exception {
        mvc.perform(post("/api/ideas").contentType(MediaType.APPLICATION_JSON).content("{nope")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.EMPLOYEE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(get("/api/ideas/not-a-uuid").header(HttpHeaders.AUTHORIZATION, bearer(Role.EMPLOYEE)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchByNonOwnerIs403() throws Exception {
        when(ideaService.updateIdea(eq(ideaId), eq(userId), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "NOT_OWNER"));

        mvc.perform(patch("/api/ideas/" + ideaId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New title\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.EMPLOYEE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_OWNER"));
    }

    @Test
    void patchRejectsBlankTitle() throws Exception {
        mvc.perform(patch("/api/ideas/" + ideaId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"     \"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.EMPLOYEE)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownIdeaIs404() throws Exception {
        when(ideaService.getIdea(ideaId, userId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "IDEA_NOT_FOUND"));

        mvc.perform(get("/api/ideas/" + ideaId).header(HttpHeaders.AUTHORIZATION, bearer(Role.EMPLOYEE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IDEA_NOT_FOUND"));
    }

    @Test
    void likeAndUnlikeReturnStatus() throws Exception {
        when(likeService.like(ideaId, userId)).thenReturn(new LikeStatusDto(3, true));
        when(likeService.unlike(ideaId, userId)).thenReturn(new LikeStatusDto(2, false));

        mvc.perform(post("/api/ideas/" + ideaId + "/like").header(HttpHeaders.AUTHORIZATION, bearer(Role.EMPLOYEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(3))
                .andExpect(jsonPath("$.likedByMe").value(true));
        mvc.perform(delete("/api/ideas/" + ideaId + "/like").header(HttpHeaders.AUTHORIZATION, bearer(Role.EMPLOYEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likedByMe").value(false));
    }

    @Test
    void stageTransitionIsOrganizerOnly() throws Exception {
        var body = "{\"decision\":\"APPROVE\",\"reason\":\"looks good\"}";
        var url = "/api/organizer/ideas/" + ideaId + "/stage-transitions";
        when(ideaService.executeStageTransition(eq(ideaId), eq(userId), any(StageTransitionRequest.class)))
                .thenReturn((IdeaDto) null);

        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.EMPLOYEE)))
                .andExpect(status().isForbidden());
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.ORGANIZER)))
                .andExpect(status().isOk());
    }

    @Test
    void stageTransitionRequiresReason() throws Exception {
        mvc.perform(post("/api/organizer/ideas/" + ideaId + "/stage-transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECT\",\"reason\":\"  \"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
