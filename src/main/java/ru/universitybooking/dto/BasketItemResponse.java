package ru.universitybooking.dto;

import java.time.LocalDateTime;

public record BasketItemResponse(
        Long id,
        Long userId,
        int room,
        LocalDateTime bookingDate,
        LocalDateTime addedAt
) {}
