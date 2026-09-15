package ru.universitybooking.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.universitybooking.entity.UniversityService;
import ru.universitybooking.service.UniversityCatalogService;

import java.util.List;

@RestController
@RequestMapping("/services")
public class ServicesController {

    private final UniversityCatalogService universityCatalogService;

    public ServicesController(UniversityCatalogService universityCatalogService) {
        this.universityCatalogService = universityCatalogService;
    }

    @GetMapping
    public List<UniversityService> getUniversityService() {
        return  universityCatalogService.getAllUniversityService();
    }
}
