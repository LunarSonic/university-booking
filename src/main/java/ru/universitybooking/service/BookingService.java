package ru.universitybooking.service;

import org.springframework.stereotype.Service;
import ru.universitybooking.dto.BookingDto;
import ru.universitybooking.entity.BookingRequest;
import ru.universitybooking.entity.StatusRequest;
import ru.universitybooking.repository.BookingRepo;

import java.time.LocalDateTime;

@Service
public class BookingService {

    private final BookingRepo bookingRepo;

    public BookingService(BookingRepo bookingRepo) {
        this.bookingRepo = bookingRepo;
    }

    public BookingDto createBooking(BookingDto dto) {
        BookingRequest booking = new BookingRequest();
        booking.setUserId(dto.userId());
        booking.setRoom(dto.room());
        booking.setBookingDate(dto.bookingDate());
        booking.setStatus(StatusRequest.NEW);
        booking.setCreateDate(LocalDateTime.now());

        BookingRequest savedBooking = bookingRepo.save(booking);
        return toDto(savedBooking);
    }

    public BookingDto getBooking(Long bookingId) {
        BookingRequest booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Cannot find request with id " + bookingId));
        return toDto(booking);
    }

    public BookingDto updateBooking(BookingDto dto) {

        Long bookingId = dto.id();
        BookingRequest booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Cannot find request with id " + bookingId));

        booking.setStatus(StatusRequest.NEW);
        booking.setCreateDate(LocalDateTime.now());

        BookingRequest savedBooking = bookingRepo.save(booking);
        return toDto(savedBooking);

    }

    public void deleteBooking(Long bookingId) {
        bookingRepo.deleteById(bookingId);
    }

    public BookingDto toDto(BookingRequest savedBooking) {
        return new BookingDto(
                savedBooking.getId(),
                savedBooking.getUserId(),
                savedBooking.getRoom(),
                savedBooking.getBookingDate(),
                savedBooking.getStatus(),
                savedBooking.getCreateDate()
        );
    }

}
