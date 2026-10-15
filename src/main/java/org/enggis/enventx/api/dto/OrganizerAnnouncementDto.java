package org.enggis.enventx.api.dto;

import org.enggis.enventx.api.domain.entity.Announcement;
import org.enggis.enventx.api.domain.enums.AnnouncementState;

import java.time.Instant;
import java.util.UUID;

public record OrganizerAnnouncementDto(
        UUID id, String title, String content, AnnouncementState state, Instant publishAt, boolean pinned,
        Instant createdAt, Instant updatedAt) {

    public static OrganizerAnnouncementDto from(Announcement a) {
        return new OrganizerAnnouncementDto(a.getId(), a.getTitle(), a.getContent(), a.getState(), a.getPublishAt(),
                a.isPinned(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
