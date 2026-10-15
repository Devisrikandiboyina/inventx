package org.enggis.enventx.api.dto;

import org.enggis.enventx.api.domain.entity.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationDto(UUID id, String type, String message, boolean read, Instant createdAt) {

    public static NotificationDto from(Notification n) {
        return new NotificationDto(n.getId(), n.getType(), n.getMessage(), n.isRead(), n.getCreatedAt());
    }
}
