package ru.universitybooking.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UniversityServiceDocument {
    private Long id;
    private String name;
    private String description;
    private Boolean active;
    private ServiceUsage usage;
}
