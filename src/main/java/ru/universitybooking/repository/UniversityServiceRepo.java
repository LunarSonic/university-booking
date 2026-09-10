package ru.universitybooking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.universitybooking.entity.UniversityService;

@Repository
public interface UniversityServiceRepo extends JpaRepository<UniversityService, Long> {
}