package ru.universitybooking.service;
import com.google.cloud.firestore.Firestore;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import ru.universitybooking.dto.ServiceDto;
import ru.universitybooking.dto.ServiceUpdateRequest;
import ru.universitybooking.store.UniversityServiceStore;
import java.util.*;
@Service
@RequiredArgsConstructor
public class UniversityCatalogService {
    private final UniversityServiceStore universityServiceStore;
    private final Firestore firestore;
    private final FirestoreOperations operations;
    @Cacheable("services")
    public List<ServiceDto> getAllUniversityService() {
        return universityServiceStore.findAll().stream()
                .map(s -> new ServiceDto(s.getId(), s.getName(), s.getDescription()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
    // Eviction is after a successful Firestore commit; outside the retryable callback.
    @CacheEvict(value="services", allEntries=true)
    public ServiceDto update(Long id, ServiceUpdateRequest request) {
        return operations.transaction(tx -> {
            var ref = firestore.collection("services").document(id.toString());
            if (!tx.get(ref).get().exists()) throw new IllegalArgumentException("Услуга не найдена");
            Map<String,Object> changes = new HashMap<>();
            changes.put("name", request.name());
            changes.put("description", request.description());
            changes.put("active", request.active());
            tx.update(ref, changes);
            return new ServiceDto(id, request.name(), request.description());
        });
    }
}
