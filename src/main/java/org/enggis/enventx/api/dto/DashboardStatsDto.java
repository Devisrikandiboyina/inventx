package org.enggis.enventx.api.dto;

import org.enggis.enventx.api.domain.enums.CategoryCode;
import org.enggis.enventx.api.domain.enums.Stage;

import java.util.Map;

/** byStage and byCategory cover every enum value, zero counts included, over the ideas the caller may see. */
public record DashboardStatsDto(
        long myIdeas,
        long myActiveIdeas,
        long likesReceived,
        long totalIdeas,
        Map<Stage, Long> byStage,
        Map<CategoryCode, Long> byCategory
) {
}
