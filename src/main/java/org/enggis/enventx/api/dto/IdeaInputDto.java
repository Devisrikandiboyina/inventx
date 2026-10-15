package org.enggis.enventx.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.enggis.enventx.api.validation.ValidCategoryCode;

import java.util.List;

public record IdeaInputDto(
        @NotBlank @Size(min = 3, max = 150) String title,
        @NotBlank @Size(max = 1000) String summary,
        @NotBlank String description,
        @NotBlank @ValidCategoryCode String category,
        @Size(max = 500) String targetCustomer,
        String businessImpact,
        @Size(max = 3, message = "A maximum of 3 team members besides the owner is allowed") List<@Valid TeamMemberDto> teamMembers
) {

    public IdeaInputDto {
        teamMembers = teamMembers == null ? List.of() : List.copyOf(teamMembers);
    }
}
