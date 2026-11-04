package ru.universitybooking.dto;

import org.springframework.format.annotation.DateTimeFormat;
import ru.universitybooking.entity.StatusRequest;
import java.time.Instant;

public record BookingSearchRequest(
        StatusRequest status,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant createdTo,
        Long userId,
        Long serviceId,
        Boolean needsProjector,
        Direction direction,
        Integer limit,
        String cursor
) {
    public enum Direction { ASC, DESC }

    public BookingSearchRequest {
        direction = direction == null ? Direction.DESC : direction;
        limit = limit == null ? 20 : limit;
    }

    public void validate() {
        if (status == null || createdFrom == null || createdTo == null) {
            throw new IllegalArgumentException(
                    "Обязательны status, createdFrom и createdTo");
        }
        Instant min = Instant.parse("0001-01-01T00:00:00Z");
        Instant max = Instant.parse("9999-12-31T23:59:59.999999999Z");
        if (createdFrom.isBefore(min) || createdTo.isAfter(max)
                || !createdFrom.isBefore(createdTo)) {
            throw new IllegalArgumentException("Некорректный диапазон дат");
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit должен быть от 1 до 100");
        }
        if ((userId != null && userId < 1)
                || (serviceId != null && serviceId < 1)) {
            throw new IllegalArgumentException("ID должны быть положительными");
        }
        if (cursor != null && (cursor.isBlank() || cursor.length() > 2048)) {
            throw new IllegalArgumentException("Некорректный cursor");
        }
    }
}