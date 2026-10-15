package org.enggis.enventx.api.dto;

import jakarta.validation.constraints.Size;
import org.enggis.enventx.api.validation.ValidCategoryCode;

import java.util.List;

/** Null fields are left unchanged; a blank adminNotes clears the notes. */
public record OrganizerIdeaPatchDto(
        @ValidCategoryCode String category,
        @Size(max = 5000) String adminNotes,
        @Size(max = 10) List<@Size(max = 30) String> tags
) {
}
