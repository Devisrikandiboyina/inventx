package org.enggis.enventx.api.repository;

import org.enggis.enventx.api.domain.entity.IdeaLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface IdeaLikeRepository extends JpaRepository<IdeaLike, UUID> {

    boolean existsByIdeaIdAndUserId(UUID ideaId, UUID userId);

    /** Idempotent: removes the like if present and returns the number of rows deleted (0 or 1). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM IdeaLike l WHERE l.idea.id = :ideaId AND l.user.id = :userId")
    int deleteByIdeaIdAndUserId(@Param("ideaId") UUID ideaId, @Param("userId") UUID userId);

    @Query("SELECT l.idea.id FROM IdeaLike l WHERE l.user.id = :userId AND l.idea.id IN :ideaIds")
    Set<UUID> findLikedIdeaIds(@Param("userId") UUID userId, @Param("ideaIds") Collection<UUID> ideaIds);
}
