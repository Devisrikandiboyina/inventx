package org.enggis.enventx.api.dto;

import org.enggis.enventx.api.domain.entity.StageConfig;
import org.enggis.enventx.api.domain.enums.Stage;

import java.time.LocalDate;

public record StageConfigDto(
        Stage stage, String displayName, int sequenceOrder, boolean enabled, LocalDate startDate, LocalDate endDate) {

    public static StageConfigDto from(StageConfig c) {
        return new StageConfigDto(c.getStage(), c.getDisplayName(), c.getSequenceOrder(), c.isEnabled(),
                c.getStartDate(), c.getEndDate());
    }
}
