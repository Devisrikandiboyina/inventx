package org.enggis.enventx.api.dto;

import org.enggis.enventx.api.domain.entity.Guideline;

import java.time.Instant;
import java.util.UUID;

public record OrganizerGuidelineDto(
        UUID id, String title, String content, int sequenceOrder, boolean published,
        String fileName, boolean hasFile, Instant createdAt, Instant updatedAt) {

    public static OrganizerGuidelineDto from(Guideline g) {
        return new OrganizerGuidelineDto(g.getId(), g.getTitle(), g.getContent(), g.getSequenceOrder(), g.isActive(),
                g.getFileName(), g.getFileKey() != null, g.getCreatedAt(), g.getUpdatedAt());
    }
}
