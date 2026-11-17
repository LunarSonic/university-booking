package ru.universitybooking.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import ru.universitybooking.scylla.*;

import java.time.Instant;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final EventJournalService eventJournalService;

    @GetMapping("/by-result")
    public EventPage getByResult(
            @RequestParam EventResult result,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor) {
        return eventJournalService.getByResult(result, from, to, limit, cursor);
    }

    @GetMapping("/by-operation")
    public EventPage getByOperation(
            @RequestParam EventOperation operation,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor) {
        return eventJournalService.getByOperation(operation, from, to, limit, cursor);
    }

    @GetMapping("/by-booking")
    public EventPage getByBooking(
            @RequestParam long bookingId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor) {
        return eventJournalService.getByBooking(bookingId, from, to, limit, cursor);
    }
}
