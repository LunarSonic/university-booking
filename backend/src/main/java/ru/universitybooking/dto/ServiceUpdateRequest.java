package ru.universitybooking.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record ServiceUpdateRequest(@NotBlank @Size(max=200) String name,
        @Size(max=2000) String description, @NotNull Boolean active) {}
