package ru.universitybooking.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.universitybooking.document.UniversityServiceDocument;
import ru.universitybooking.service.UniversityCatalogService;

import java.util.List;

@RestController
@RequestMapping("/services")
@RequiredArgsConstructor
public class ServicesController {

    private final UniversityCatalogService universityCatalogService;

    @GetMapping
    public List<UniversityServiceDocument> getUniversityService() {
        return universityCatalogService.getAllUniversityService();
    }
}
