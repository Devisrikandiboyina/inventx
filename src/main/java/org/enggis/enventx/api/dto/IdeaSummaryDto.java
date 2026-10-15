package org.enggis.enventx.api.dto;

import org.enggis.enventx.api.domain.entity.Idea;
import org.enggis.enventx.api.domain.enums.CategoryCode;
import org.enggis.enventx.api.domain.enums.IdeaStatus;
import org.enggis.enventx.api.domain.enums.Stage;

import java.time.Instant;
import java.util.UUID;

public record IdeaSummaryDto(
        UUID id,
        String title,
        String summary,
        CategoryCode category,
        Stage stage,
        IdeaStatus status,
        UUID ownerId,
        String ownerName,
        long likeCount,
        boolean likedByMe,
        Instant createdAt,
        Instant updatedAt
) {

    public static IdeaSummaryDto from(Idea idea, boolean likedByMe) {
        return new IdeaSummaryDto(
                idea.getId(), idea.getTitle(), idea.getSummary(), idea.getCategory(), idea.getStage(),
                idea.getStatus(), idea.getOwner().getId(), idea.getOwner().getName(), idea.getLikeCount(),
                likedByMe, idea.getCreatedAt(), idea.getUpdatedAt());
    }
}
