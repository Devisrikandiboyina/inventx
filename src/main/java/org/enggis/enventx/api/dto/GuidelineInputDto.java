package org.enggis.enventx.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuidelineInputDto(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 20000) String content,
        @Min(0) Integer sequenceOrder,
        Boolean published
) {
}
