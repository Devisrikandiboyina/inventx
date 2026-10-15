package org.enggis.enventx.api.dto;

import org.enggis.enventx.api.domain.entity.StageConfig;
import org.enggis.enventx.api.domain.enums.Stage;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record OrganizerStageDto(
        Stage stage, String displayName, int sequenceOrder, boolean enabled,
        LocalDate startDate, LocalDate endDate, UUID updatedById, String updatedByName, Instant updatedAt) {

    public static OrganizerStageDto from(StageConfig c) {
        var by = c.getUpdatedBy();
        return new OrganizerStageDto(c.getStage(), c.getDisplayName(), c.getSequenceOrder(), c.isEnabled(),
                c.getStartDate(), c.getEndDate(), by == null ? null : by.getId(), by == null ? null : by.getName(),
                c.getUpdatedAt());
    }
}
