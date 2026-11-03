package ru.universitybooking.store;

import ru.universitybooking.document.BookingDocument;

import java.util.List;
import java.util.Optional;

public interface BookingStore {
    Optional<BookingDocument> findById(Long id);
    List<BookingDocument> findAll();
    BookingDocument save(BookingDocument bookingDocument);
    void delete(Long id);
}
