package org.enggis.enventx.api.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Null fields are left unchanged. */
public record AnnouncementPatchDto(
        @Size(max = 200) String title,
        @Size(max = 10000) String content,
        Boolean pinned,
        Instant publishAt
) {
}
