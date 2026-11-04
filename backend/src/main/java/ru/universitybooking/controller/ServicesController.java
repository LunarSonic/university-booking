package ru.universitybooking.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.universitybooking.dto.ServiceDto;
import ru.universitybooking.dto.ServiceUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ru.universitybooking.service.UniversityCatalogService;

import java.util.List;

@RestController
@RequestMapping("/services")
@RequiredArgsConstructor
public class ServicesController {

    private final UniversityCatalogService universityCatalogService;

    @GetMapping
    public List<ServiceDto> getUniversityService() {
        return universityCatalogService.getAllUniversityService();
    }

    @PatchMapping("/{id}")
    public ServiceDto update(@PathVariable Long id, @RequestBody @Valid ServiceUpdateRequest request) {
        return universityCatalogService.update(id, request);
    }
}
