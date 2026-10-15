package org.enggis.enventx.api.repository;

import org.enggis.enventx.api.domain.entity.IdeaAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdeaAttachmentRepository extends JpaRepository<IdeaAttachment, UUID> {

    List<IdeaAttachment> findByIdeaIdOrderByCreatedAtAsc(UUID ideaId);

    Optional<IdeaAttachment> findByIdAndIdeaId(UUID id, UUID ideaId);

    long countByIdeaId(UUID ideaId);
}
