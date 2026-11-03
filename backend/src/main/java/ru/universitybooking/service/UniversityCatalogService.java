package ru.universitybooking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.universitybooking.document.UniversityServiceDocument;
import ru.universitybooking.store.UniversityServiceStore;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UniversityCatalogService {

    private final UniversityServiceStore universityServiceStore;

    @Cacheable("services")
    public List<UniversityServiceDocument> getAllUniversityService() {
        return universityServiceStore.findAll();
    }
}
