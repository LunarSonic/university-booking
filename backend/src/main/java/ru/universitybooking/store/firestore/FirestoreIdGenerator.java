package ru.universitybooking.store.firestore;

import com.google.cloud.firestore.Firestore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ExecutionException;

@Component
@RequiredArgsConstructor
public class FirestoreIdGenerator {
    private final Firestore firestore;

    public Long getNextBookingId() {
        try {
            return firestore.runTransaction(transaction -> {
                        var ref = firestore.collection("counters").document("bookings");
                        var snapshot = transaction.get(ref).get();
                        long nextId = 1L;
                        if (snapshot.exists() && snapshot.contains("lastId")) {
                            Long lastId = snapshot.getLong("lastId");
                            nextId = (lastId != null ? lastId : 0L) + 1;
                        }
                        transaction.set(ref, Map.of("lastId", nextId));
                        return nextId;
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Не удалось сгенерировать ID заявки", e);
        }
    }
}
