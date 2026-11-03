package ru.universitybooking.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingDetails {
    private Integer participants;
    private Boolean needsProjector;
    private String comment;
}
