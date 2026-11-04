package ru.universitybooking.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record BookingDetailsDto(
        @Positive Integer participants,
        Boolean needsProjector,
        @Size(max = 1000) String comment
) {}
