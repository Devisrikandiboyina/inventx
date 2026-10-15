package org.enggis.enventx.api.dto;

import org.enggis.enventx.api.domain.enums.CategoryCode;
import org.enggis.enventx.api.domain.enums.IdeaStatus;
import org.enggis.enventx.api.domain.enums.Stage;

import java.util.Map;

public record OrganizerDashboardDto(
        long totalIdeas,
        long awaitingReview,
        long totalUsers,
        Map<IdeaStatus, Long> byStatus,
        Map<Stage, Long> byStage,
        Map<CategoryCode, Long> byCategory
) {
}
