package org.enggis.enventx.api.repository;

import org.enggis.enventx.api.domain.entity.IdeaTeamMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IdeaTeamMemberRepository extends JpaRepository<IdeaTeamMember, UUID> {

    List<IdeaTeamMember> findByIdeaIdOrderByCreatedAtAsc(UUID ideaId);

    void deleteByIdeaId(UUID ideaId);
}
