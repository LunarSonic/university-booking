package ru.universitybooking.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.universitybooking.entity.UniversityService;

import java.util.List;


@Service
public class UniversityCatalogService {

    @Cacheable("services")
    public List<UniversityService> getAllUniversityService()
    {

        try{
            Thread.sleep(3000);
        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }

        return List.of(new UniversityService(1L, "Аренда аудитории", "Бронирование учебной аудитории для занятий"),
                new UniversityService(2L, "Аренда проектора", "Прокат проектора для мероприятий"),
                new UniversityService(3L, "Аренда конференц-зала", "Бронирование зала для конференций и семинаров"),
                new UniversityService(4L, "Консультация с научным руководителем", "Запись на консультацию"));
    }

}
