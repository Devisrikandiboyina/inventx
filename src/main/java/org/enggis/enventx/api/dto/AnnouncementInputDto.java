package org.enggis.enventx.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** New announcements are always created as DRAFT; use the state endpoint to publish. */
public record AnnouncementInputDto(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 10000) String content,
        Boolean pinned,
        Instant publishAt
) {
}
