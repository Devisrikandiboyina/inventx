package org.enggis.enventx.api.repository;

import jakarta.persistence.LockModeType;
import org.enggis.enventx.api.domain.entity.Idea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdeaRepository extends JpaRepository<Idea, UUID>, JpaSpecificationExecutor<Idea> {

    @Override
    @EntityGraph(attributePaths = "owner")
    org.springframework.data.domain.Page<Idea> findAll(
            org.springframework.data.jpa.domain.Specification<Idea> spec, org.springframework.data.domain.Pageable pageable);

    @EntityGraph(attributePaths = "owner")
    List<Idea> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    @Query("SELECT COALESCE(SUM(i.likeCount), 0) FROM Idea i")
    long sumAllLikes();

    /**
     * Plain count of the owner's non-archived ideas. Hibernate silently drops a pessimistic lock on aggregate
     * queries, so this must be called after {@link UserRepository#findByIdForUpdate} has locked the owner row
     * inside the same transaction; that lock is what makes the max-5-active-ideas check race-free.
     */
    @Query("SELECT COUNT(i) FROM Idea i WHERE i.owner.id = :ownerId "
            + "AND i.status <> org.enggis.enventx.api.domain.enums.IdeaStatus.ARCHIVED")
    long countActiveIdeas(@Param("ownerId") UUID ownerId);

    /** Row lock serialising concurrent submit / stage-transition requests on the same idea. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Idea i WHERE i.id = :id")
    Optional<Idea> findByIdForUpdate(@Param("id") UUID id);

    /** Atomic in-database increment so concurrent likes never lose updates (no read-modify-write). */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Idea i SET i.likeCount = i.likeCount + 1 WHERE i.id = :ideaId")
    int incrementLikeCount(@Param("ideaId") UUID ideaId);

    /** Atomic decrement that never goes below zero. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Idea i SET i.likeCount = i.likeCount - 1 WHERE i.id = :ideaId AND i.likeCount > 0")
    int decrementLikeCount(@Param("ideaId") UUID ideaId);

    @Query("SELECT i.likeCount FROM Idea i WHERE i.id = :ideaId")
    Optional<Long> findLikeCountById(@Param("ideaId") UUID ideaId);

    /** Repairs any drift between the denormalized counter and the idea_likes rows. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Idea i SET i.likeCount = (SELECT COUNT(l) FROM IdeaLike l WHERE l.idea.id = i.id)")
    int recalculateAllLikeCounts();

    long countByOwnerId(UUID ownerId);

    @Query("SELECT COALESCE(SUM(i.likeCount), 0) FROM Idea i WHERE i.owner.id = :ownerId")
    long sumLikesReceived(@Param("ownerId") UUID ownerId);

    @Query("SELECT i.stage, COUNT(i) FROM Idea i WHERE i.status NOT IN :hidden OR i.owner.id = :viewerId GROUP BY i.stage")
    List<Object[]> countVisibleByStage(@Param("viewerId") UUID viewerId,
                                       @Param("hidden") java.util.Collection<org.enggis.enventx.api.domain.enums.IdeaStatus> hidden);

    @Query("SELECT i.category, COUNT(i) FROM Idea i WHERE i.status NOT IN :hidden OR i.owner.id = :viewerId GROUP BY i.category")
    List<Object[]> countVisibleByCategory(@Param("viewerId") UUID viewerId,
                                          @Param("hidden") java.util.Collection<org.enggis.enventx.api.domain.enums.IdeaStatus> hidden);

    @Query("SELECT i.status, COUNT(i) FROM Idea i GROUP BY i.status")
    List<Object[]> countAllByStatus();

    @Query("SELECT i.stage, COUNT(i) FROM Idea i GROUP BY i.stage")
    List<Object[]> countAllByStage();

    @Query("SELECT i.category, COUNT(i) FROM Idea i GROUP BY i.category")
    List<Object[]> countAllByCategory();
}