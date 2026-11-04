package ru.universitybooking.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.universitybooking.document.*;
import ru.universitybooking.dto.*;
import ru.universitybooking.entity.StatusRequest;
import ru.universitybooking.exception.BookingNotFoundException;
import ru.universitybooking.mapper.BookingMapper;
import ru.universitybooking.store.BookingStore;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BookingService {
    private final BookingStore bookingStore;
    private final BookingMapper mapper;
    private final Firestore firestore;
    private final FirestoreOperations operations;

    public BookingDto createBooking(BookingDto dto) { return create(dto, null); }

    public Optional<BookingDto> completedCheckout(Long basketId) {
        return operations.transaction(tx -> {
            var marker = tx.get(firestore.collection("checkouts").document(basketId.toString())).get();
            if (!marker.exists()) return Optional.empty();
            var booking = tx.get(firestore.collection("bookings")
                    .document(marker.getLong("bookingId").toString())).get();
            if (!booking.exists()) throw new IllegalArgumentException("Оформленная заявка уже удалена");
            return Optional.of(mapper.toDto(booking.toObject(BookingDocument.class)));
        });
    }

    public BookingDto checkout(Long basketId, BookingDto dto) { return create(dto, basketId); }

    private BookingDto create(BookingDto dto, Long basketId) {
        return operations.transaction(tx -> {
            var markerRef = basketId == null ? null : firestore.collection("checkouts").document(basketId.toString());
            if (markerRef != null) {
                var marker = tx.get(markerRef).get();
                if (marker.exists()) {
                    var existing = tx.get(firestore.collection("bookings")
                            .document(marker.getLong("bookingId").toString())).get();
                    if (!existing.exists()) throw new IllegalArgumentException("Оформленная заявка уже удалена");
                    return mapper.toDto(existing.toObject(BookingDocument.class));
                }
            }
            var serviceRef = firestore.collection("services").document(dto.serviceId().toString());
            var service = tx.get(serviceRef).get();
            requireActive(service);
            var counterRef = firestore.collection("counters").document("bookings");
            var counter = tx.get(counterRef).get();
            Long lastId = counter.exists() ? counter.getLong("lastId") : null;
            long id = (lastId == null ? 0 : lastId) + 1;
            var ref = firestore.collection("bookings").document(Long.toString(id));
            if (tx.get(ref).get().exists()) throw new IllegalStateException("Восстановите счетчик ID миграцией");
            var booking = mapper.toDocument(dto);
            booking.setId(id);
            var now = Timestamp.now();
            booking.setCreateDate(now);
            booking.setUpdatedAt(now);
            booking.setStatus(new BookingStatusDocument(StatusRequest.NEW.name(), now));
            tx.set(counterRef, Map.of("lastId", id));
            tx.create(ref, booking);
            tx.update(serviceRef, "usage.bookingsCount", count(service) + 1);
            if (markerRef != null) tx.create(markerRef, Map.of("bookingId", id));
            return mapper.toDto(booking);
        });
    }

    public BookingDto getBooking(Long id) {
        return mapper.toDto(bookingStore.findById(id)
                .orElseThrow(() -> new BookingNotFoundException("Заявка не найдена: " + id)));
    }

    public BookingDto updateBooking(Long id, StatusRequest status) {
        return change(id, status, null, null);
    }

    public BookingDto updateDetails(Long id, BookingDetailsDto details) {
        return change(id, null, null, details);
    }

    public BookingDto assign(Long id, BookingAssignmentRequest request) {
        return change(id, null, request.serviceId(), request.details());
    }

    private BookingDto change(Long id, StatusRequest status, Long newServiceId, BookingDetailsDto details) {
        return operations.transaction(tx -> {
            var ref = firestore.collection("bookings").document(id.toString());
            var snapshot = tx.get(ref).get();
            if (!snapshot.exists()) throw new BookingNotFoundException("Заявка не найдена: " + id);
            var booking = snapshot.toObject(BookingDocument.class);
            var oldRef = firestore.collection("services").document(booking.getServiceId().toString());
            boolean reassigned = newServiceId != null && !newServiceId.equals(booking.getServiceId());
            DocumentSnapshot oldService = null, newService = null;
            DocumentReference newRef = null;
            if (newServiceId != null) {
                newRef = firestore.collection("services").document(newServiceId.toString());
                newService = tx.get(newRef).get();
                requireActive(newService);
                if (reassigned) {
                    oldService = tx.get(oldRef).get();
                    if (!oldService.exists() || count(oldService) < 1)
                        throw new IllegalStateException("Некорректный счетчик услуги");
                }
            }
            var changed = new ArrayList<String>();
            Timestamp now = Timestamp.now();
            if (status != null && !status.name().equals(booking.getStatus().getCode())) {
                booking.setStatus(new BookingStatusDocument(status.name(), now));
                changed.add("status");
            }
            if (details != null) {
                var replacement = new BookingDetails(details.participants(), details.needsProjector(), details.comment());
                if (!replacement.equals(booking.getDetails())) {
                    booking.setDetails(replacement);
                    changed.add("details");
                }
            }
            if (reassigned) { booking.setServiceId(newServiceId); changed.add("serviceId"); }
            if (changed.isEmpty()) return mapper.toDto(booking);
            int revision = (booking.getRevision() == null ? 0 : booking.getRevision()) + 1;
            booking.setRevision(revision);
            booking.setUpdatedAt(now);
            String day = Instant.ofEpochSecond(now.getSeconds(), now.getNanos())
                    .atOffset(ZoneOffset.UTC).toLocalDate().toString();
            var dailyRef = firestore.collection("bookingChangeDaily").document(day);
            var eventRef = firestore.collection("bookingChanges").document(id + "_" + revision);
            // All reads above, all writes below. A retry repeats the whole transaction.
            tx.set(ref, booking);
            if (reassigned) {
                tx.update(oldRef, "usage.bookingsCount", count(oldService) - 1);
                tx.update(newRef, "usage.bookingsCount", count(newService) + 1);
            }
            tx.create(eventRef, Map.of("bookingId", id, "revision", revision,
                    "changedAt", now, "type", "UPDATE", "fields", changed));
            tx.set(dailyRef, Map.of("day", day, "changesCount", FieldValue.increment(1)), SetOptions.merge());
            return mapper.toDto(booking);
        });
    }

    public void deleteBooking(Long id) {
        operations.transaction(tx -> {
            var ref = firestore.collection("bookings").document(id.toString());
            var booking = tx.get(ref).get();
            if (!booking.exists()) throw new BookingNotFoundException("Заявка не найдена: " + id);
            var serviceRef = firestore.collection("services").document(booking.getLong("serviceId").toString());
            var service = tx.get(serviceRef).get();
            if (!service.exists() || count(service) < 1) throw new IllegalStateException("Некорректный счетчик услуги");
            tx.delete(ref);
            tx.update(serviceRef, "usage.bookingsCount", count(service) - 1);
            return null;
        });
    }

    private static void requireActive(DocumentSnapshot service) {
        if (!service.exists() || Boolean.FALSE.equals(service.getBoolean("active")))
            throw new IllegalArgumentException("Услуга не найдена или отключена");
    }
    private static long count(DocumentSnapshot service) {
        Long count = service.getLong("usage.bookingsCount");
        return count == null ? 0 : count;
    }

    public List<BookingDto> getBookingsByStatus(StatusRequest status) {
        return bookingStore.findByStatus(status)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    public List<BookingDto> getBookingsByStatusAndUserId(StatusRequest status, Long userId) {
        return bookingStore.findByStatusAndUserId(status, userId)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    public BookingPage<BookingDto> searchBookings(BookingSearchRequest request) {
        request.validate();
        BookingPage<BookingDocument> page = bookingStore.search(request);
        return new BookingPage<>(
                page.items().stream().map(mapper::toDto).toList(),
                page.nextCursor());
    }
}
