package org.enggis.enventx.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

/** Criterion name to weight; the weights must add up to exactly 100. */
public record UpdateSettingsRequest(
        @NotEmpty Map<@NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{0,39}") String,
                @NotNull @Min(0) @Max(100) Integer> judgingCriteria
) {
}
