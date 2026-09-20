package ru.universitybooking.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.universitybooking.entity.UniversityService;
import ru.universitybooking.repository.UniversityServiceRepo;

import java.util.List;

@Service
public class UniversityCatalogService {

    private final UniversityServiceRepo serviceRepository;

    public UniversityCatalogService(UniversityServiceRepo serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    @Cacheable("services")
    public List<UniversityService> getAllUniversityService() {
        return serviceRepository.findAll();
    }
}
