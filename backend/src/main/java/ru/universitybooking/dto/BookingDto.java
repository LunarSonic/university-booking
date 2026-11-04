package ru.universitybooking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import ru.universitybooking.entity.StatusRequest;

import java.time.LocalDateTime;

public record BookingDto(
        Long id,
        @NotNull Long userId,
        @NotNull Long serviceId,
        @Positive int room,
        @NotNull @Future LocalDateTime bookingDate,
        StatusRequest statusRequest,
        LocalDateTime createDate,
        @Valid
        @JsonInclude(JsonInclude.Include.NON_NULL)
        BookingDetailsDto details
){
    public BookingDto(Long id, Long userId, Long serviceId, int room,
                      LocalDateTime bookingDate, StatusRequest statusRequest,
                      LocalDateTime createDate) {
        this(id, userId, serviceId, room, bookingDate,
                statusRequest, createDate, null);
    }
}
