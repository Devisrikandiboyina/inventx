package org.enggis.enventx.api.service;

import lombok.RequiredArgsConstructor;
import org.enggis.enventx.api.domain.enums.CategoryCode;
import org.enggis.enventx.api.domain.enums.IdeaStatus;
import org.enggis.enventx.api.domain.enums.Stage;
import org.enggis.enventx.api.dto.DashboardStatsDto;
import org.enggis.enventx.api.repository.IdeaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final List<IdeaStatus> HIDDEN_FROM_OTHERS = List.of(IdeaStatus.DRAFT, IdeaStatus.ARCHIVED);

    private final IdeaRepository ideas;

    @Transactional(readOnly = true)
    public DashboardStatsDto statsFor(UUID userId) {
        var byStage = new EnumMap<Stage, Long>(Stage.class);
        for (var stage : Stage.values()) {
            byStage.put(stage, 0L);
        }
        ideas.countVisibleByStage(userId, HIDDEN_FROM_OTHERS)
                .forEach(row -> byStage.put((Stage) row[0], (Long) row[1]));

        var byCategory = new EnumMap<CategoryCode, Long>(CategoryCode.class);
        for (var category : CategoryCode.values()) {
            byCategory.put(category, 0L);
        }
        ideas.countVisibleByCategory(userId, HIDDEN_FROM_OTHERS)
                .forEach(row -> byCategory.put((CategoryCode) row[0], (Long) row[1]));




        return new DashboardStatsDto(
                ideas.countByOwnerId(userId),
                ideas.countActiveIdeas(userId),
                ideas.sumLikesReceived(userId),
                byStage.values().stream().mapToLong(Long::longValue).sum(),
                byStage,
                byCategory);
    }
}
