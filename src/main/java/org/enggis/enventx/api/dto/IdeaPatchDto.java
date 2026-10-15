package org.enggis.enventx.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.enggis.enventx.api.validation.ValidCategoryCode;

import java.util.List;

/** Partial update: a null field is left unchanged; a non-null {@code teamMembers} replaces the whole team. */
public record IdeaPatchDto(
        @Size(min = 3, max = 150) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String title,
        @Size(max = 1000) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String summary,
        @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String description,
        @ValidCategoryCode @Pattern(regexp = ".*\\S.*", message = "must not be blank") String category,
        @Size(max = 500) String targetCustomer,
        String businessImpact,
        @Size(max = 3, message = "A maximum of 3 team members besides the owner is allowed") List<@Valid TeamMemberDto> teamMembers
) {

    public IdeaPatchDto {
        teamMembers = teamMembers == null ? null : List.copyOf(teamMembers);
    }
}
