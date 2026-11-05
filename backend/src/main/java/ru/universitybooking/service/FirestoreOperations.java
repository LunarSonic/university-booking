package ru.universitybooking.service;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.universitybooking.exception.BookingNotFoundException;

import java.util.concurrent.ExecutionException;

@Component
@RequiredArgsConstructor
public class FirestoreOperations {

    private final Firestore firestore;

    public <T> T transaction(Transaction.Function<T> action) {
        try {
            return firestore.runTransaction(action).get();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Операция Firestore прервана", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            while (cause != null) {
                switch (cause) {
                    case IllegalArgumentException error -> throw error;
                    case IllegalStateException error -> throw error;
                    case BookingNotFoundException error -> throw error;
                    default -> cause = cause.getCause();
                }
            }
            throw new IllegalStateException("Ошибка транзакции Firestore", e);
        }
    }
}
