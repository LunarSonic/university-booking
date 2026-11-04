package ru.universitybooking.store.firestore;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldPath;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.universitybooking.document.BookingDocument;
import ru.universitybooking.entity.StatusRequest;
import ru.universitybooking.store.BookingStore;
import ru.universitybooking.dto.BookingPage;
import ru.universitybooking.dto.BookingSearchRequest;
import java.time.Instant;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Component
@RequiredArgsConstructor
public class FirestoreBookingStore implements BookingStore {
    private final Firestore firestore;

    @Override
    public Optional<BookingDocument> findById(Long id) {
        try {
            DocumentSnapshot doc = firestore
                    .collection("bookings")
                    .document(String.valueOf(id))
                    .get().get();
            if (!doc.exists()) {
                return Optional.empty();
            }
            return Optional.ofNullable(doc.toObject(BookingDocument.class));
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Ошибка чтения заявки id=" + id, e);
        }
    }

    @Override
    public List<BookingDocument> findAll() {
        try {
            return firestore.collection("bookings")
                    .get().get()
                    .getDocuments()
                    .stream()
                    .map(doc -> doc.toObject(BookingDocument.class))
                    .toList();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Ошибка чтения коллекции bookings", e);
        }
    }

    @Override
    public List<BookingDocument> findByStatus(StatusRequest status) {
        try{
            return firestore.collection("bookings")
                    .whereEqualTo("status.code", status.name())
                    .get().get()
                    .getDocuments()
                    .stream()
                    .map(doc -> doc.toObject(BookingDocument.class))
                    .toList();

        }
        catch (InterruptedException e){
            Thread.currentThread().interrupt();
            throw new RuntimeException("Поиск заявок был прерван", e);
        }
        catch (ExecutionException e){
            throw new RuntimeException("Ошибка поиска заявок по статусу", e);
        }
    }

    @Override
    public List<BookingDocument> findByStatusAndUserId(StatusRequest status,  Long userId) {
        try{
            return firestore.collection("bookings")
                    .whereEqualTo("status.code", status.name())
                    .whereEqualTo("userId", userId)
                    .get().get()
                    .getDocuments()
                    .stream()
                    .map(doc -> doc.toObject(BookingDocument.class))
                    .toList();

        }
        catch (InterruptedException e){
            Thread.currentThread().interrupt();
            throw new RuntimeException("Поиск заявок был прерван", e);
        }
        catch (ExecutionException e){
            throw new RuntimeException("Ошибка поиска заявок по статусу и пользователю", e);
        }
    }

    @Override
    public BookingDocument save(BookingDocument bookingDocument) {
        try {
            firestore.collection("bookings")
                    .document(String.valueOf(bookingDocument.getId()))
                    .set(bookingDocument).get();
            return bookingDocument;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Ошибка сохранения заявки id=" + bookingDocument.getId(), e);
        }
    }

    @Override
    public void delete(Long id) {
        try {
            firestore.collection("bookings")
                    .document(String.valueOf(id))
                    .delete().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Ошибка удаления заявки id=" + id, e);
        }
    }

    @Override
    public BookingPage<BookingDocument> search(BookingSearchRequest request) {
        request.validate();
        try {
            Query.Direction direction = request.direction() == BookingSearchRequest.Direction.ASC
                    ? Query.Direction.ASCENDING : Query.Direction.DESCENDING;

            Query query = firestore.collection("bookings")
                    .whereEqualTo("status.code", request.status().name())
                    .whereGreaterThanOrEqualTo("createDate", timestamp(request.createdFrom()))
                    .whereLessThan("createDate", timestamp(request.createdTo()));

            if (request.userId() != null) {
                query = query.whereEqualTo("userId", request.userId());
            }
            if (request.serviceId() != null) {
                query = query.whereEqualTo("serviceId", request.serviceId());
            }
            if (request.needsProjector() != null) {
                query = query.whereEqualTo("details.needsProjector", request.needsProjector());
            }

            query = query.orderBy("createDate", direction)
                    .orderBy(FieldPath.documentId(), direction);

            if (request.cursor() != null) {
                BookingCursor cursor = BookingCursor.decode(request.cursor(), request);
                query = query.startAfter(timestamp(cursor.createdAt()), cursor.documentId());
            }

            var documents = query.limit(request.limit() + 1)
                    .get().get().getDocuments();
            boolean hasMore = documents.size() > request.limit();
            var visible = documents.subList(0, Math.min(documents.size(), request.limit()));

            List<BookingDocument> items = visible.stream()
                    .map(doc -> doc.toObject(BookingDocument.class))
                    .toList();

            String nextCursor = null;
            if (hasMore) {
                var last = visible.get(visible.size() - 1);
                Timestamp date = last.getTimestamp("createDate");
                nextCursor = new BookingCursor(
                        Instant.ofEpochSecond(date.getSeconds(), date.getNanos()),
                        last.getId()).encode(request);
            }
            return new BookingPage<>(items, nextCursor);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Поиск заявок был прерван", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Ошибка поиска заявок", e);
        }
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.ofTimeSecondsAndNanos(value.getEpochSecond(), value.getNano());
    }
}
