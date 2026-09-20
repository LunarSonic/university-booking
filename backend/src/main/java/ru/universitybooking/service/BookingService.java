package ru.universitybooking.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.universitybooking.dto.BookingDto;
import ru.universitybooking.entity.BookingRequest;
import ru.universitybooking.entity.StatusRequest;
import ru.universitybooking.exception.BookingNotFoundException;
import ru.universitybooking.repository.BookingRepo;
import ru.universitybooking.repository.UniversityServiceRepo;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BookingService {

    private final BookingRepo bookingRepo;
    private final UniversityServiceRepo universityServiceRepo;

    public BookingService(BookingRepo bookingRepo, UniversityServiceRepo universityServiceRepo) {
        this.bookingRepo = bookingRepo;
        this.universityServiceRepo = universityServiceRepo;
    }

    @Transactional
    public BookingDto createBooking(BookingDto dto) {
        if (!universityServiceRepo.existsById(dto.serviceId())) {
            throw new IllegalArgumentException("Услуга с id " + dto.serviceId() + " не найдена");
        }

        BookingRequest booking = new BookingRequest();
        booking.setUserId(dto.userId());
        booking.setServiceId(dto.serviceId());
        booking.setRoom(dto.room());
        booking.setBookingDate(dto.bookingDate());
        booking.setStatus(StatusRequest.NEW);
        booking.setCreateDate(LocalDateTime.now());

        BookingRequest savedBooking = bookingRepo.save(booking);
        return toDto(savedBooking);
    }

    @Transactional(readOnly = true)
    public BookingDto getBooking(Long bookingId) {
        BookingRequest booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Cannot find request with id " + bookingId));
        return toDto(booking);
    }

    @Transactional
    public BookingDto updateBooking(Long bookingId, StatusRequest newStatus) {
        BookingRequest booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Cannot find request with id " + bookingId));

        booking.setStatus(newStatus);

        BookingRequest savedBooking = bookingRepo.save(booking);
        return toDto(savedBooking);

    }

    @Transactional
    public void deleteBooking(Long bookingId) {
        if (!bookingRepo.existsById(bookingId)) {
            throw new BookingNotFoundException("Cannot find request with id " + bookingId);
        }
        bookingRepo.deleteById(bookingId);
    }

    @Transactional(readOnly = true)
    public List<BookingDto> getBookingsByStatus(StatusRequest status) {
        return bookingRepo.findByStatus(status)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingDto> getBookingsByStatusAndUserId(StatusRequest status, Long userId) {
        return bookingRepo.findByStatusAndUserId(status, userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private BookingDto toDto(BookingRequest savedBooking) {
        return new BookingDto(
                savedBooking.getId(),
                savedBooking.getUserId(),
                savedBooking.getServiceId(),
                savedBooking.getRoom(),
                savedBooking.getBookingDate(),
                savedBooking.getStatus(),
                savedBooking.getCreateDate()
        );
    }

}
