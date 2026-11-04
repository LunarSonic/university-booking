package ru.universitybooking.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import ru.universitybooking.dto.BookingAssignmentRequest;
import ru.universitybooking.dto.BookingDetailsDto;
import ru.universitybooking.dto.BookingDto;
import ru.universitybooking.dto.BookingPage;
import ru.universitybooking.dto.BookingSearchRequest;
import ru.universitybooking.entity.StatusRequest;
import ru.universitybooking.service.BookingService;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingDto createBooking(@RequestBody @Valid BookingDto dto) {
        return bookingService.createBooking(dto);
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public BookingDto getBooking(@PathVariable Long id) {
        return bookingService.getBooking(id);
    }

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public BookingDto updateBooking(@PathVariable Long id, @RequestParam StatusRequest status) {
        return bookingService.updateBooking(id, status);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBooking(@PathVariable Long id) {
        bookingService.deleteBooking(id);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<BookingDto> getBookings(@RequestParam StatusRequest status,
                                        @RequestParam(required = false) Long userId) {
        if (userId != null) {
            return bookingService.getBookingsByStatusAndUserId(status, userId);
        }
        return bookingService.getBookingsByStatus(status);
    }

    @GetMapping("/search")
    public BookingPage<BookingDto> searchBookings(
            @ModelAttribute BookingSearchRequest request,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            throw new IllegalArgumentException(
                    "Некорректные параметры поиска: "
                            + bindingResult.getFieldErrors().stream()
                            .map(error -> error.getField())
                            .distinct()
                            .toList());
        }
        return bookingService.searchBookings(request);
    }

    @PatchMapping("/{id}/details")
    public BookingDto updateDetails(@PathVariable Long id,
            @RequestBody @Valid BookingDetailsDto details) {
        return bookingService.updateDetails(id, details);
    }

    @PatchMapping("/{id}/assignment")
    public BookingDto assign(@PathVariable Long id,
            @RequestBody @Valid BookingAssignmentRequest request) {
        return bookingService.assign(id, request);
    }
}
