package org.enggis.enventx.api.controller;

import org.enggis.enventx.api.dto.AuthResponse;
import org.enggis.enventx.api.dto.RegisterRequest;
import org.enggis.enventx.api.dto.UserDto;
import org.enggis.enventx.api.security.JwtService;
import org.enggis.enventx.api.security.SecurityConfig;
import org.enggis.enventx.api.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({AuthController.class, SecurityConfig.class, AuthControllerTest.Config.class})
@TestPropertySource(properties = {
        "app.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        "app.cors.allowed-origins=http://localhost:3000"
})
class AuthControllerTest {

    @SpringBootConfiguration
    static class Config {
    }

    @Autowired MockMvc mvc;
    @MockitoBean AuthService authService;
    @MockitoBean JwtService jwtService;

    private static String body(String email) {
        return """
                {"name":"Test User","email":"%s","password":"Sup3rSecret!"}""".formatted(email);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a@enggis.com", "b@nexsys.co.in", "c@stability.co", "D@ENGGIS.COM"})
    void signupWithAllowedDomainIsCreated(String email) throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new AuthResponse(null, "jwt-token"));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("jwt-token"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a@gmail.com", "b@enggis.com.evil.io", "c@evilenggis.com", "d@sub.stability.co", "not-an-email"})
    void signupWithRejectedDomainIsBadRequestAndNeverReachesService(String email) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body(email)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }

    @Test
    void signupIsPublicWithoutBearerToken() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new AuthResponse((UserDto) null, "t"));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body("x@stability.co")))
                .andExpect(status().is2xxSuccessful());
    }
}
