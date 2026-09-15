package ru.universitybooking.dto;

import java.time.LocalDateTime;

public record BasketItemResponse(
        Long id,
        Long userId,
        Long serviceId,
        int room,
        LocalDateTime bookingDate,
        LocalDateTime addedAt
) {}
