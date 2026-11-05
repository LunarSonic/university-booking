package ru.universitybooking.mapper;

import com.google.cloud.Timestamp;
import org.springframework.stereotype.Component;
import ru.universitybooking.document.BookingDetails;
import ru.universitybooking.document.BookingDocument;
import ru.universitybooking.document.BookingStatusDocument;
import ru.universitybooking.dto.BookingDetailsDto;
import ru.universitybooking.dto.BookingDto;
import ru.universitybooking.entity.StatusRequest;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class BookingMapper {

    public BookingDto toDto(BookingDocument doc) {
        return new BookingDto(
                doc.getId(),
                doc.getUserId(),
                doc.getServiceId(),
                doc.getRoom(),
                toLocalDateTime(doc.getBookingDate()),
                doc.getStatus() != null ? StatusRequest.valueOf(doc.getStatus().getCode())
                        : doc.getStatusCode() != null ? StatusRequest.valueOf(doc.getStatusCode()) : StatusRequest.NEW,
                toLocalDateTime(doc.getCreateDate()),
                doc.getDetails() == null ? null : new BookingDetailsDto(
                        doc.getDetails().getParticipants(),
                        doc.getDetails().getNeedsProjector(),
                        doc.getDetails().getComment())
        );
    }

    public BookingDocument toDocument(BookingDto dto) {
        return BookingDocument.builder()
                .id(dto.id())
                .userId(dto.userId())
                .serviceId(dto.serviceId())
                .room(dto.room())
                .bookingDate(toTimestamp(dto.bookingDate()))
                .createDate(dto.createDate() != null ? toTimestamp(dto.createDate()) : Timestamp.now())
                .updatedAt(Timestamp.now())
                .status(BookingStatusDocument.builder()
                        .code(dto.statusRequest() != null ? dto.statusRequest().name() : StatusRequest.NEW.name())
                        .changedAt(Timestamp.now())
                        .build())
                .revision(1)
                .details(dto.details() == null ? null : BookingDetails.builder()
                        .participants(dto.details().participants())
                        .needsProjector(dto.details().needsProjector())
                        .comment(dto.details().comment())
                        .build())
                .schemaVersion(2)
                .build();
    }

    public LocalDateTime toLocalDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return LocalDateTime.ofInstant(
                Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()), ZoneOffset.UTC);
    }

    public Timestamp toTimestamp(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        Instant instant = dateTime.toInstant(ZoneOffset.UTC);
        return Timestamp.ofTimeSecondsAndNanos(
                instant.getEpochSecond(),
                instant.getNano());
    }
}
