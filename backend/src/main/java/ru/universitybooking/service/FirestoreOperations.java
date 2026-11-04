package ru.universitybooking.service;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.concurrent.ExecutionException;

@Component
@RequiredArgsConstructor
public class FirestoreOperations {
    private final Firestore firestore;

    public <T> T transaction(Transaction.Function<T> action) {
        try { return firestore.runTransaction(action).get(); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Операция Firestore прервана", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            while (cause != null) {
                if (cause instanceof IllegalArgumentException error) throw error;
                if (cause instanceof IllegalStateException error) throw error;
                if (cause instanceof ru.universitybooking.exception.BookingNotFoundException error) throw error;
                cause = cause.getCause();
            }
            throw new IllegalStateException("Ошибка транзакции Firestore", e);
        }
    }
}
