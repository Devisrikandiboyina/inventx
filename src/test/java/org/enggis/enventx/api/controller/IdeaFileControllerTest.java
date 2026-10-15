package org.enggis.enventx.api.controller;

import org.enggis.enventx.api.domain.enums.Role;
import org.enggis.enventx.api.dto.AttachmentDto;
import org.enggis.enventx.api.dto.SignedLinkDto;
import org.enggis.enventx.api.security.JwtService;
import org.enggis.enventx.api.security.SecurityConfig;
import org.enggis.enventx.api.security.UserPrincipal;
import org.enggis.enventx.api.service.IdeaFileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IdeaFileController.class)
@Import({IdeaFileController.class, SecurityConfig.class, JwtService.class, GlobalExceptionHandler.class,
        IdeaFileControllerTest.Config.class})
@TestPropertySource(properties = {
        "app.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        "app.cors.allowed-origins=http://localhost:3000"
})
class IdeaFileControllerTest {

    @SpringBootConfiguration
    static class Config {
    }

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean IdeaFileService files;

    private final UUID userId = UUID.randomUUID();
    private final UUID ideaId = UUID.randomUUID();

    private String bearer() {
        return "Bearer " + jwt.generateToken(new UserPrincipal(userId, "u@enggis.com", "x", Role.EMPLOYEE));
    }

    private final MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[20]);

    @Test
    void uploadsRequireAuthentication() throws Exception {
        mvc.perform(multipart("/api/ideas/" + ideaId + "/attachments").file(file)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/guidelines/" + ideaId + "/file-link")).andExpect(status().isUnauthorized());
    }

    @Test
    void attachmentUploadReturns201() throws Exception {
        when(files.addAttachment(eq(ideaId), eq(userId), any()))
                .thenReturn(new AttachmentDto(UUID.randomUUID(), "a.pdf", "application/pdf", 20, Instant.now()));

        mvc.perform(multipart("/api/ideas/" + ideaId + "/attachments").file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("a.pdf"));
    }

    @Test
    void missingFilePartIsBadRequest() throws Exception {
        mvc.perform(multipart("/api/ideas/" + ideaId + "/poster").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void linkEndpointsReturnUrlAndExpiry() throws Exception {
        var aid = UUID.randomUUID();
        when(files.attachmentLink(ideaId, aid, userId)).thenReturn(new SignedLinkDto("https://x/y", Instant.now()));

        mvc.perform(get("/api/ideas/" + ideaId + "/attachments/" + aid + "/link")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://x/y"))
                .andExpect(jsonPath("$.expiresAt").exists());
    }
}
