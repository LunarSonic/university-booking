package ru.universitybooking.scylla;

import java.time.Instant;
import java.util.UUID;

public record EventRecord(
        UUID eventId,
        Instant occurredAt,
        EventOperation operation,
        EventResult result,
        Long bookingId,
        String errorCode
) {}
