package org.enggis.enventx.api.dto;

import java.util.List;

/** Never contains the access token or recipient numbers. */
public record WhatsAppIntegrationStatusDto(
        boolean configured,
        String provider,
        boolean supportsGroupDelivery,
        String deliveryMode,
        int recipientCount,
        String templateName,
        String adminWorkflowUrl,
        List<String> missingSettings,
        WhatsAppAttemptDto lastAttempt
) {
}
