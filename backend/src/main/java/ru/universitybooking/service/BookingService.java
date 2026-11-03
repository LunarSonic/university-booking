package ru.universitybooking.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.universitybooking.document.BookingDocument;
import ru.universitybooking.document.BookingStatusDocument;
import ru.universitybooking.dto.BookingDto;
import ru.universitybooking.entity.StatusRequest;
import ru.universitybooking.exception.BookingNotFoundException;
import ru.universitybooking.mapper.BookingMapper;
import ru.universitybooking.store.BookingStore;
import ru.universitybooking.store.UniversityServiceStore;
import ru.universitybooking.store.firestore.FirestoreIdGenerator;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingStore bookingStore;
    private final UniversityServiceStore universityServiceStore;
    private final FirestoreIdGenerator firestoreIdGenerator;
    private final BookingMapper mapper;
    private final Firestore firestore;

    public BookingDto createBooking(BookingDto dto) {
        if (universityServiceStore.findById(dto.serviceId()).isEmpty()) {
            throw new IllegalArgumentException("Услуга с id " + dto.serviceId() + " не найдена");
        }
        Long id = firestoreIdGenerator.getNextBookingId();
        BookingDocument bookingDocument = mapper.toDocument(dto);
        bookingDocument.setId(id);

        try {
            firestore.runTransaction(transaction -> {
                var bookingRef = firestore.collection("bookings").document(String.valueOf(id));
                var serviceRef = firestore.collection("services").document(String.valueOf(dto.serviceId()));
                var serviceSnapshot = transaction.get(serviceRef).get();
                long current = 0L;
                if (serviceSnapshot.exists() && serviceSnapshot.contains("usage.bookingsCount")) {
                    Long val = serviceSnapshot.getLong("usage.bookingsCount");
                    current = val != null ? val : 0L;
                }
                transaction.set(bookingRef, bookingDocument);
                transaction.update(serviceRef, "usage.bookingsCount", current + 1);
                return null;
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Ошибка при создании заявки", e);
        }
        return mapper.toDto(bookingDocument);
    }

    public BookingDto getBooking(Long bookingId) {
        BookingDocument booking = bookingStore.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Заявка с id " + bookingId + " не найдена"));
        return mapper.toDto(booking);
    }

    public BookingDto updateBooking(Long bookingId, StatusRequest newStatus) {
        BookingDocument booking = bookingStore.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Заявка с id " + bookingId + " не найдена"));
        booking.setStatus(BookingStatusDocument.builder()
                .code(newStatus.name())
                .changedAt(Timestamp.now())
                .build());
        booking.setUpdatedAt(Timestamp.now());
        booking.setRevision(booking.getRevision() + 1);

        BookingDocument savedBooking = bookingStore.save(booking);
        return mapper.toDto(savedBooking);
    }

    public void deleteBooking(Long bookingId) {
        BookingDocument bookingDocument = bookingStore.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Заявка с id " + bookingId + " не найдена"));
        try {
            firestore.runTransaction(transaction -> {
                var bookingRef = firestore.collection("bookings")
                        .document(String.valueOf(bookingId));
                var serviceRef = firestore.collection("services")
                        .document(String.valueOf(bookingDocument.getServiceId()));

                var serviceSnap = transaction.get(serviceRef).get();
                long current = 0L;
                if (serviceSnap.exists() && serviceSnap.contains("usage.bookingsCount")) {
                    Long val = serviceSnap.getLong("usage.bookingsCount");
                    current = val != null ? val : 0L;
                }

                transaction.delete(bookingRef);
                transaction.update(serviceRef, "usage.bookingsCount", Math.max(0, current - 1));
                return null;
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Ошибка при удалении заявки", e);
        }
    }

    public List<BookingDto> getBookingsByStatus(StatusRequest status) {
        return bookingStore.findAll()
                .stream()
                .filter(doc -> matchesStatus(doc, status))
                .map(mapper::toDto)
                .toList();
    }

    public List<BookingDto> getBookingsByStatusAndUserId(StatusRequest status, Long userId) {
        return bookingStore.findAll()
                .stream()
                .filter(doc -> matchesStatus(doc, status) && userId.equals(doc.getUserId()))
                .map(mapper::toDto)
                .toList();
    }

    private boolean matchesStatus(BookingDocument doc, StatusRequest status) {
        if (doc.getStatus() != null) {
            return status.name().equals(doc.getStatus().getCode());
        }
        return doc.getStatusCode() != null && status.name().equals(doc.getStatusCode());
    }
}
