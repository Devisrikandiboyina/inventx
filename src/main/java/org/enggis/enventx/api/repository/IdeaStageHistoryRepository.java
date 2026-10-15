package org.enggis.enventx.api.repository;

import org.enggis.enventx.api.domain.entity.IdeaStageHistory;
import org.enggis.enventx.api.domain.enums.StageDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdeaStageHistoryRepository extends JpaRepository<IdeaStageHistory, UUID> {

    List<IdeaStageHistory> findByIdeaIdOrderByChangedAtAsc(UUID ideaId);

    Optional<IdeaStageHistory> findFirstByIdeaIdAndDecisionOrderByChangedAtDesc(UUID ideaId, StageDecision decision);
}
