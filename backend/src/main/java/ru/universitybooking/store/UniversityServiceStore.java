package ru.universitybooking.store;

import ru.universitybooking.document.UniversityServiceDocument;

import java.util.List;
import java.util.Optional;

public interface UniversityServiceStore {
    List<UniversityServiceDocument> findAll();
    Optional<UniversityServiceDocument> findById(Long id);
    UniversityServiceDocument save(UniversityServiceDocument universityServiceDocument);
}
