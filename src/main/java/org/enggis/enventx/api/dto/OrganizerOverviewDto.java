
package org.enggis.enventx.api.dto;

import org.enggis.enventx.api.domain.enums.CategoryCode;
import org.enggis.enventx.api.domain.enums.IdeaStatus;
import org.enggis.enventx.api.domain.enums.Stage;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record OrganizerOverviewDto(
        long totalIdeas,
        long submittedIdeas,
        long participants,
        long totalLikes,
        long inScreening,
        Map<IdeaStatus, Long> byStatus,
        Map<Stage, Long> byStage,
        Map<CategoryCode, Long> byCategory,
        List<UpcomingDeadlineDto> upcomingDeadlines
) {
    public record UpcomingDeadlineDto(
            Stage stage,
            String name,
            int sequenceOrder,
            LocalDate startDate,
            LocalDate endDate,
            boolean enabled
    ) {}
}
