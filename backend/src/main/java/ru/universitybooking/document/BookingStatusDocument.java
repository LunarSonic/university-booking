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
public class BookingStatusDocument {
    private String code;
    private Timestamp changedAt;
}
