package org.enggis.enventx.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** {@code confirmation} must be true: it is the organizer's explicit acknowledgement that dates are changing. */
public record StageDatesRequest(@NotNull LocalDate startDate, LocalDate endDate, boolean confirmation) {
}
