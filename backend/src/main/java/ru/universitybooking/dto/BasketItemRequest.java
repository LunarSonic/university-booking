package ru.universitybooking.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record BasketItemRequest(
        @NotNull Long userId,
        @NotNull Long serviceId,
        @Positive int room,
        @NotNull @Future LocalDateTime bookingDate
) {}
