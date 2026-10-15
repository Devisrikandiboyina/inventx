package org.enggis.enventx.api.dto;

import org.enggis.enventx.api.domain.enums.CategoryCode;
import org.enggis.enventx.api.domain.enums.IdeaStatus;
import org.enggis.enventx.api.domain.enums.Stage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IdeaDto(
        UUID id,
        String title,
        String summary,
        String description,
        CategoryCode category,
        String targetCustomer,
        String businessImpact,
        Stage stage,
        IdeaStatus status,
        UUID ownerId,
        String ownerName,
        long likeCount,
        boolean likedByMe,
        List<TeamMemberDto> teamMembers,
        Instant createdAt,
        Instant updatedAt,
        String adminNotes,
        List<String> tags
) {
}
