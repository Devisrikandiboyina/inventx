package org.enggis.enventx.api.service;

import lombok.RequiredArgsConstructor;
import org.enggis.enventx.api.domain.entity.StageConfig;
import org.enggis.enventx.api.domain.enums.CategoryCode;
import org.enggis.enventx.api.domain.enums.IdeaStatus;
import org.enggis.enventx.api.domain.enums.Stage;
import org.enggis.enventx.api.dto.OrganizerDashboardDto;
import org.enggis.enventx.api.dto.OrganizerOverviewDto;
import org.enggis.enventx.api.repository.IdeaRepository;
import org.enggis.enventx.api.repository.StageConfigRepository;
import org.enggis.enventx.api.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizerDashboardService {

    private final IdeaRepository ideas;
    private final UserRepository users;
    private final StageConfigRepository stages;

    public OrganizerDashboardDto stats() {
        var byStatus = counts(IdeaStatus.class, ideas.countAllByStatus());
        var byStage = counts(Stage.class, ideas.countAllByStage());
        var byCategory = counts(CategoryCode.class, ideas.countAllByCategory());
        var total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        var awaiting = byStatus.get(IdeaStatus.SUBMITTED) + byStatus.get(IdeaStatus.UNDER_REVIEW);
        return new OrganizerDashboardDto(total, awaiting, users.count(), byStatus, byStage, byCategory);
    }

    public OrganizerOverviewDto overview() {
        var byStatus = counts(
                IdeaStatus.class, ideas.countAllByStatus());

        var byStage = counts(
                Stage.class, ideas.countAllByStage());

        var byCategory = counts(
                CategoryCode.class, ideas.countAllByCategory());

        var total = byStatus.values()
                .stream()
                .mapToLong(Long::longValue)
                .sum();

        var submitted = byStatus.get(IdeaStatus.SUBMITTED);

        var screening = byStatus.get(IdeaStatus.UNDER_REVIEW);

        var deadlines = stages.findAllByEnabledTrueOrderBySequenceOrderAsc()
                .stream()
                .map(this::toDeadline)
                .toList();

        return new OrganizerOverviewDto(
                total,
                submitted,
                users.count(),
                ideas.sumAllLikes(),
                screening,
                byStatus,
                byStage,
                byCategory,
                deadlines
        );
    }

    private OrganizerOverviewDto.UpcomingDeadlineDto toDeadline(
            StageConfig config) {
        return new OrganizerOverviewDto.UpcomingDeadlineDto(
                config.getStage(),
                config.getDisplayName(),
                config.getSequenceOrder(),
                config.getStartDate(),
                config.getEndDate(),
                config.isEnabled()
        );
    }

    /** Every enum value is present, zero when no idea matches. */
    private static <E extends Enum<E>> Map<E, Long> counts(Class<E> type, List<Object[]> rows) {
        var result = new EnumMap<E, Long>(type);
        for (var constant : type.getEnumConstants()) {
            result.put(constant, 0L);
        }
        for (var row : rows) {
            result.put(type.cast(row[0]), ((Number) row[1]).longValue());
        }
        return result;
    }
}
