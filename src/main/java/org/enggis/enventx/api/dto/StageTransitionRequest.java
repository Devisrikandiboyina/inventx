package org.enggis.enventx.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.enggis.enventx.api.domain.enums.Stage;
import org.enggis.enventx.api.domain.enums.StageDecision;

public record StageTransitionRequest(
        @NotNull StageDecision decision,
        @NotBlank @Size(min = 5, max = 2000) String reason,
        Stage toStage
) {

    public StageTransitionRequest {
        reason = reason == null ? null : reason.strip();
    }
}
