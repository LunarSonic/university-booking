package ru.universitybooking.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import ru.universitybooking.entity.UniversityService;
import ru.universitybooking.repository.UniversityServiceRepo;

import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UniversityServiceRepo serviceRepository;

    public DataInitializer(UniversityServiceRepo serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    @Override
    public void run(String... args) {
        if (serviceRepository.count() == 0) {
            serviceRepository.saveAll(List.of(
                    new UniversityService(null, "Аренда аудитории", "Бронирование учебной аудитории для занятий"),
                    new UniversityService(null, "Аренда проектора", "Прокат проектора для мероприятий"),
                    new UniversityService(null, "Аренда конференц-зала", "Бронирование зала для конференций и семинаров"),
                    new UniversityService(null, "Консультация с научным руководителем", "Запись на консультацию")
            ));
        }
    }
}
