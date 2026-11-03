package ru.universitybooking.document;

import com.google.cloud.Timestamp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingDocument {
    private Long id;
    private Long userId;
    private Long serviceId;
    private Integer room;
    private Timestamp bookingDate;
    private Timestamp createDate;
    private Timestamp updatedAt;
    private String statusCode;          // v1: строковый статус
    private BookingStatusDocument status; // v2: вложенный объект
    private BookingDetails details;
    private Integer revision;
    private Integer schemaVersion;
}
