package org.enggis.enventx.api.dto;

import jakarta.validation.constraints.NotNull;
import org.enggis.enventx.api.domain.enums.AnnouncementState;

public record AnnouncementStateRequest(@NotNull AnnouncementState state) {
}
