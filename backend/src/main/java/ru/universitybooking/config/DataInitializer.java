package ru.universitybooking.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.universitybooking.document.ServiceUsage;
import ru.universitybooking.document.UniversityServiceDocument;
import ru.universitybooking.store.UniversityServiceStore;

import java.util.List;

@Component
@Profile("!migration")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UniversityServiceStore universityServiceStore;

    @Override
    public void run(String... args) {
        List<UniversityServiceDocument> existing = universityServiceStore.findAll();
        if (!existing.isEmpty()) {
            return;
        }
        List<UniversityServiceDocument> services = List.of(
                buildService(1L, "Аренда аудитории", "Бронирование учебной аудитории для занятий"),
                buildService(2L, "Аренда проектора", "Прокат проектора для мероприятий"),
                buildService(3L, "Аренда конференц-зала", "Бронирование зала для конференций и семинаров"),
                buildService(4L, "Консультация с научным руководителем", "Запись на консультацию")
        );

        services.forEach(universityServiceStore::save);
    }

    private UniversityServiceDocument buildService(Long id, String name, String description) {
        return UniversityServiceDocument.builder()
                .id(id)
                .name(name)
                .description(description)
                .active(true)
                .usage(ServiceUsage.builder().bookingsCount(0L).build())
                .build();
    }
}
