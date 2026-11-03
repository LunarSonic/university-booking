package ru.universitybooking.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.universitybooking.document.BookingStatusDocument;
import ru.universitybooking.store.BookingStore;

@Slf4j
@Component
@Profile("migration")
@RequiredArgsConstructor
public class SchemaV1ToV2Migrator {

    private final BookingStore bookingStore;

    @Value("${migration.dry-run:false}")
    private boolean dryRun;

    public void migrate() {
        var all = bookingStore.findAll();
        int scanned = 0, migrated = 0, skipped = 0, failed = 0;

        for (var doc : all) {
            scanned++;
            try {
                if (doc.getSchemaVersion() != null && doc.getSchemaVersion() == 2) {
                    skipped++;
                    continue;
                }

                if (doc.getSchemaVersion() != null && doc.getSchemaVersion() == 1
                        && doc.getStatusCode() != null) {
                    if (!dryRun) {
                        String code = doc.getStatusCode();
                        doc.setStatus(BookingStatusDocument.builder()
                                .code(code)
                                .changedAt(null)
                                .build());
                        doc.setStatusCode(null);
                        doc.setSchemaVersion(2);
                        bookingStore.save(doc);
                        log.info("[v1->v2] id={} statusCode='{}' -> status объект", doc.getId(), code);
                    }
                    migrated++;
                    continue;
                }

                log.warn("[v1->v2] id={} пропущена: schemaVersion={} statusCode={}", doc.getId(), doc.getSchemaVersion(), doc.getStatusCode());
                skipped++;

            } catch (Exception e) {
                log.error("[v1->v2] ошибка id={}: {}", doc.getId(), e.getMessage());
                failed++;
            }
        }

        log.info("[v1->v2] scanned={} migrated={} skipped={} failed={}", scanned, migrated, skipped, failed);
    }
}
