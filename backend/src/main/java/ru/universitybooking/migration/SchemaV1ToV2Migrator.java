package ru.universitybooking.migration;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.universitybooking.entity.StatusRequest;
import ru.universitybooking.service.FirestoreOperations;
import java.util.*;

@Slf4j
@Component
@Profile("migration")
@RequiredArgsConstructor
public class SchemaV1ToV2Migrator {
    private final Firestore firestore;
    private final FirestoreOperations operations;
    @Value("${migration.dry-run:false}") private boolean dryRun;
    public void migrate() {
        int scanned=0, migrated=0, skipped=0, failed=0;
        DocumentSnapshot last = null;
        try {
            while (true) {
                Query query = firestore.collection("bookings").orderBy(FieldPath.documentId()).limit(100);
                if (last != null) query = query.startAfter(last);
                var page = query.get().get().getDocuments();
                if (page.isEmpty()) break;
                for (var entry : page) {
                    scanned++;
                    try {
                        boolean changed = operations.transaction(tx -> {
                            var doc = tx.get(entry.getReference()).get();
                            if (!doc.exists()) return false;
                            Object status = doc.get("status");
                            if (status instanceof Map<?,?> map && map.get("code") instanceof String code) {
                                StatusRequest.valueOf(code);
                                if (Long.valueOf(2).equals(doc.getLong("schemaVersion"))) return false;
                                if (!dryRun) tx.update(doc.getReference(), "schemaVersion", 2);
                                return true;
                            }
                            String code = status instanceof String s ? s : doc.getString("statusCode");
                            if (code == null) throw new IllegalArgumentException("Нет строкового состояния");
                            StatusRequest.valueOf(code);
                            if (!dryRun) {
                                Map<String,Object> nested = new HashMap<>();
                                nested.put("code", code); nested.put("changedAt", null);
                                tx.update(doc.getReference(), "status", nested,
                                        "statusCode", FieldValue.delete(), "schemaVersion", 2);
                            }
                            return true;
                        });
                        if (changed) migrated++; else skipped++;
                    } catch (Exception e) { failed++; log.error("Миграция id={}: {}", entry.getId(), e.getMessage()); }
                }
                last = page.get(page.size()-1);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IllegalStateException(e);
        } catch (java.util.concurrent.ExecutionException e) { throw new IllegalStateException(e); }
        log.info("[v1->v2] dryRun={} scanned={} migrated={} skipped={} failed={}", dryRun, scanned, migrated, skipped, failed);
        if (failed > 0) throw new IllegalStateException("Миграция завершилась с ошибками: " + failed);
    }
}
