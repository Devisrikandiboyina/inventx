package org.enggis.enventx.api.whatsapp;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CloudApiWhatsAppClientTest {

    private final WhatsAppProperties props = new WhatsAppProperties(
            "https://graph.test/v21.0", "12345", "secret-token", "announcement", "en", List.of("919876543210"), null);

    @Test
    void sendsTemplateWithBearerTokenAndReturnsMessageId() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://graph.test/v21.0/12345/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer secret-token"))
                .andExpect(jsonPath("$.messaging_product").value("whatsapp"))
                .andExpect(jsonPath("$.to").value("919876543210"))
                .andExpect(jsonPath("$.type").value("template"))
                .andExpect(jsonPath("$.template.name").value("announcement"))
                .andExpect(jsonPath("$.template.language.code").value("en"))
                .andExpect(jsonPath("$.template.components[0].parameters[1].text").value("Body"))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.ABC\"}]}", MediaType.APPLICATION_JSON));

        var id = new CloudApiWhatsAppClient(props, builder).sendTemplate("919876543210", "announcement", "en",
                List.of("Title", "Body"));

        assertEquals("wamid.ABC", id);
        server.verify();
    }

    @Test
    void providerErrorsBecomeSendExceptionsWithoutLeakingTheToken() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://graph.test/v21.0/12345/messages"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"error\":{\"message\":\"Invalid OAuth access token\"}}")
                        .contentType(MediaType.APPLICATION_JSON));

        var e = assertThrows(WhatsAppSendException.class, () -> new CloudApiWhatsAppClient(props, builder)
                .sendTemplate("919876543210", "announcement", "en", List.of("T", "B")));

        assertTrue(e.getMessage().contains("HTTP 401"));
        assertFalse(e.getMessage().contains("secret-token"));
    }

    @Test
    void responseWithoutMessageIdIsAnError() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://graph.test/v21.0/12345/messages"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThrows(WhatsAppSendException.class, () -> new CloudApiWhatsAppClient(props, builder)
                .sendTemplate("919876543210", "announcement", "en", List.of("T", "B")));
    }

    @Test
    void propertiesNormaliseRecipientsAndOnlyAllowHttpsWorkflowUrls() {
        var p = new WhatsAppProperties(null, " ", "t", "tpl", null,
                List.of("+91 98765-43210", "bad", "919876543210", "12"), "http://insecure.example");

        assertEquals(List.of("919876543210"), p.recipients());
        assertEquals("https://graph.facebook.com/v21.0", p.apiBaseUrl());
        assertEquals("en", p.languageCode());
        assertNull(p.adminWorkflowUrl());
        assertFalse(p.configured());
        assertEquals(List.of("whatsapp.phone-number-id"), p.missingSettings());
    }
}