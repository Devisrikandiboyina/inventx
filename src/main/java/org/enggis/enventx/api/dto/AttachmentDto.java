package org.enggis.enventx.api.dto;

import org.enggis.enventx.api.domain.entity.IdeaAttachment;

import java.time.Instant;
import java.util.UUID;

public record AttachmentDto(UUID id, String fileName, String contentType, long sizeBytes, Instant createdAt) {

    public static AttachmentDto from(IdeaAttachment a) {
        return new AttachmentDto(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes(), a.getCreatedAt());
    }
}
