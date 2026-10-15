package org.enggis.enventx.api.dto;

import org.enggis.enventx.api.domain.entity.WhatsAppDeliveryAttempt;
import org.enggis.enventx.api.domain.enums.DeliveryStatus;

import java.time.Instant;
import java.util.UUID;

public record WhatsAppAttemptDto(
        UUID id, UUID announcementId, DeliveryStatus status, String providerMessageId, String error,
        int recipientCount, int deliveredCount, UUID attemptedById, Instant attemptedAt) {

    public static WhatsAppAttemptDto from(WhatsAppDeliveryAttempt a) {
        return new WhatsAppAttemptDto(a.getId(), a.getAnnouncement().getId(), a.getStatus(), a.getProviderMessageId(),
                a.getError(), a.getRecipientCount(), a.getDeliveredCount(), a.getAttemptedBy().getId(),
                a.getAttemptedAt());
    }
}
