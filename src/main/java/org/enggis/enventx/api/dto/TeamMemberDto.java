package org.enggis.enventx.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamMemberDto(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 150) String department,
        @NotBlank @Size(max = 100) String roleInTeam
) {
}
