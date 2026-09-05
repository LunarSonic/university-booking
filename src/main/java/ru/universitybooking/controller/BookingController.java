package ru.universitybooking.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.universitybooking.dto.BookingDto;
import ru.universitybooking.entity.StatusRequest;
import ru.universitybooking.service.BookingService;

@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

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
}
