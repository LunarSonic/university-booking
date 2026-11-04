package ru.universitybooking.store;

import ru.universitybooking.document.BookingDocument;
import ru.universitybooking.dto.BookingPage;
import ru.universitybooking.dto.BookingSearchRequest;
import ru.universitybooking.entity.StatusRequest;

import java.util.List;
import java.util.Optional;

public interface BookingStore {
    Optional<BookingDocument> findById(Long id);
    List<BookingDocument> findAll();
    List<BookingDocument> findByStatus(StatusRequest status);
    List<BookingDocument> findByStatusAndUserId(StatusRequest status, Long userId);
    void save(BookingDocument bookingDocument);
    void delete(Long id);
    BookingPage<BookingDocument> search(BookingSearchRequest request);
}
