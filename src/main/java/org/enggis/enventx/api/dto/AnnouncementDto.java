package org.enggis.enventx.api.dto;

import org.enggis.enventx.api.domain.entity.Announcement;

import java.time.Instant;
import java.util.UUID;

public record AnnouncementDto(UUID id, String title, String content, Instant publishedAt, boolean pinned) {

    public static AnnouncementDto from(Announcement a) {
        return new AnnouncementDto(a.getId(), a.getTitle(), a.getContent(), a.getPublishAt(), a.isPinned());
    }
}
