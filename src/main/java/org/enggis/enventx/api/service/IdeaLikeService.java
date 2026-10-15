package org.enggis.enventx.api.service;

import org.enggis.enventx.api.domain.entity.IdeaLike;
import org.enggis.enventx.api.domain.enums.IdeaStatus;
import org.enggis.enventx.api.dto.LikeStatusDto;
import org.enggis.enventx.api.repository.IdeaLikeRepository;
import org.enggis.enventx.api.repository.IdeaRepository;
import org.enggis.enventx.api.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Keeps {@code Idea.likeCount} in step with the {@code idea_likes} rows. Both operations are idempotent and safe
 * under concurrency: the unique (idea_id, user_id) constraint decides who inserted the like, and the counter is
 * only changed (atomically, in SQL) by the caller whose insert/delete actually changed a row.
 */
@Service
public class IdeaLikeService {

    private final IdeaRepository ideas;
    private final IdeaLikeRepository likes;
    private final UserRepository users;
    private final TransactionTemplate tx;

    public IdeaLikeService(IdeaRepository ideas, IdeaLikeRepository likes, UserRepository users,
                           PlatformTransactionManager transactionManager) {
        this.ideas = ideas;
        this.likes = likes;
        this.users = users;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public LikeStatusDto like(UUID ideaId, UUID userId) {
        try {
            return tx.execute(status -> doLike(ideaId, userId));
        } catch (DataIntegrityViolationException e) {
            // A concurrent request by the same user inserted the like first; that transaction owns the increment.
            if (likes.existsByIdeaIdAndUserId(ideaId, userId)) {
                return new LikeStatusDto(currentCount(ideaId), true);
            }
            throw e;
        }
    }

    public LikeStatusDto unlike(UUID ideaId, UUID userId) {
        return tx.execute(status -> {
            if (!ideas.existsById(ideaId)) {
                throw notFound(ideaId);
            }
            if (likes.deleteByIdeaIdAndUserId(ideaId, userId) > 0) {
                ideas.decrementLikeCount(ideaId);
            }
            return new LikeStatusDto(currentCount(ideaId), false);
        });
    }

    private LikeStatusDto doLike(UUID ideaId, UUID userId) {
        var idea = ideas.findById(ideaId).orElseThrow(() -> notFound(ideaId));
        if (idea.getStatus() == IdeaStatus.DRAFT || idea.getStatus() == IdeaStatus.ARCHIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ideas in status " + idea.getStatus() + " cannot be liked");
        }
        if (likes.existsByIdeaIdAndUserId(ideaId, userId)) {
            return new LikeStatusDto(idea.getLikeCount(), true);
        }
        var like = new IdeaLike();
        like.setIdea(idea);
        like.setUser(users.getReferenceById(userId));
        likes.saveAndFlush(like);
        ideas.incrementLikeCount(ideaId);
        return new LikeStatusDto(currentCount(ideaId), true);
    }

    private long currentCount(UUID ideaId) {
        return ideas.findLikeCountById(ideaId).orElseThrow(() -> notFound(ideaId));
    }

    private static ResponseStatusException notFound(UUID ideaId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Idea not found: " + ideaId);
    }
}
