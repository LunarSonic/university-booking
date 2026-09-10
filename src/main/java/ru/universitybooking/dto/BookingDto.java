package ru.universitybooking.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import ru.universitybooking.entity.StatusRequest;

import java.time.LocalDateTime;

public record BookingDto(
        Long id,
        @NotNull Long userId,
        @NotNull Long serviceId,
        @Positive Integer room,
        @Future LocalDateTime bookingDate,
        StatusRequest statusRequest,
        LocalDateTime createDate
){
}
