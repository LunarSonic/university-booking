package ru.universitybooking.store.firestore;

import com.google.cloud.firestore.Firestore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.universitybooking.document.UniversityServiceDocument;
import ru.universitybooking.store.UniversityServiceStore;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Component
@RequiredArgsConstructor
public class FirestoreUniversityServiceStore implements UniversityServiceStore {
    private final Firestore firestore;

    @Override
    public List<UniversityServiceDocument> findAll() {
        try {
            return firestore.collection("services")
                    .get().get()
                    .getDocuments()
                    .stream()
                    .map(doc -> doc.toObject(UniversityServiceDocument.class))
                    .toList();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Ошибка чтения коллекции services", e);
        }
    }

    @Override
    public Optional<UniversityServiceDocument> findById(Long id) {
        try {
            var snapshot = firestore.collection("services")
                    .document(String.valueOf(id))
                    .get().get();
            if (!snapshot.exists()) {
                return Optional.empty();
            }
            return Optional.ofNullable(snapshot.toObject(UniversityServiceDocument.class));
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Ошибка чтения услуги id=" + id, e);
        }
    }

    @Override
    public UniversityServiceDocument save(UniversityServiceDocument universityServiceDocument) {
        try {
            firestore.collection("services")
                    .document(String.valueOf(universityServiceDocument.getId()))
                    .set(universityServiceDocument).get();
            return universityServiceDocument;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Ошибка сохранения услуги id=" + universityServiceDocument.getId(), e);
        }
    }
}
