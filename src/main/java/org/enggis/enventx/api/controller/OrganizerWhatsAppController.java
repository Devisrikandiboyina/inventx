package org.enggis.enventx.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.enggis.enventx.api.config.OpenApiConfig;
import org.enggis.enventx.api.dto.WhatsAppAttemptDto;
import org.enggis.enventx.api.dto.WhatsAppIntegrationStatusDto;
import org.enggis.enventx.api.security.UserPrincipal;
import org.enggis.enventx.api.service.WhatsAppService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/organizer")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_ORGANIZER)
public class OrganizerWhatsAppController {

    private final WhatsAppService whatsApp;

    @GetMapping("/integrations/whatsapp")
    @Operation(summary = "Integration status; supportsGroupDelivery is always false for the Cloud API")
    public WhatsAppIntegrationStatusDto status() {
        return whatsApp.status();
    }

    @PostMapping("/announcements/{id}/whatsapp-notifications")
    @Operation(summary = "Send a published announcement via template messages; NOT_CONFIGURED is reported, never faked")
    public WhatsAppAttemptDto send(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id) {
        return whatsApp.send(id, me.id());
    }
}
