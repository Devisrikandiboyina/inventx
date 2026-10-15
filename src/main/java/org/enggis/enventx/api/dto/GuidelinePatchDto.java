package org.enggis.enventx.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Null fields are left unchanged. */
public record GuidelinePatchDto(
        @Size(max = 200) String title,
        @Size(max = 20000) String content,
        @Min(0) Integer sequenceOrder,
        Boolean published
) {
}
