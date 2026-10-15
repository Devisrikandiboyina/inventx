package org.enggis.enventx.api.dto;

import jakarta.validation.constraints.NotNull;
import org.enggis.enventx.api.domain.enums.Role;

public record RoleChangeRequest(@NotNull Role role) {
}
