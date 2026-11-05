package ru.universitybooking.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BookingAssignmentRequest(@NotNull @Positive Long serviceId, @NotNull @Valid BookingDetailsDto details) {}
