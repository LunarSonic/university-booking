package ru.universitybooking.store.firestore;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.universitybooking.document.BookingDocument;
import ru.universitybooking.store.BookingStore;

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
}
