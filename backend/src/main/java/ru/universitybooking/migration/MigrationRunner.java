package ru.universitybooking.migration;

import com.google.cloud.Timestamp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.universitybooking.document.BookingDocument;
import ru.universitybooking.document.ServiceUsage;
import ru.universitybooking.document.UniversityServiceDocument;
import ru.universitybooking.entity.BookingRequest;
import ru.universitybooking.entity.UniversityService;
import ru.universitybooking.repository.BookingRepo;
import ru.universitybooking.repository.UniversityServiceRepo;
import ru.universitybooking.store.BookingStore;
import ru.universitybooking.store.UniversityServiceStore;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Component
@Profile("migration")
@RequiredArgsConstructor
public class MigrationRunner implements CommandLineRunner {

    private final com.google.cloud.firestore.Firestore firestore;
    private final ru.universitybooking.service.FirestoreOperations operations;
    private final BookingRepo bookingRepo;
    private final UniversityServiceRepo universityServiceRepo;
    private final BookingStore bookingStore;
    private final UniversityServiceStore universityServiceStore;
    private final SchemaV1ToV2Migrator schemaV1ToV2Migrator;

    @Value("${migration.dry-run:false}")
    private boolean dryRun;

    @Override
    public void run(String... args) {
        log.info("Миграция запущена. dry-run={}", dryRun);
        log.info("PostgreSQL → Firestore");
        migrateServices();
        migrateBookings();
        log.info("Firestore v1 -> v2");
        schemaV1ToV2Migrator.migrate();
        if (!dryRun) rebuildCounters();
        log.info("Миграция завершена");
    }

    private void migrateServices() {
        var all = universityServiceRepo.findAll();
        int migrated = 0, skipped = 0;

        for (UniversityService s : all) {
            if (universityServiceStore.findById(s.getId()).isPresent()) {
                log.info("[services] пропущена id={}", s.getId());
                skipped++;
                continue;
            }
            if (!dryRun) {
                universityServiceStore.save(UniversityServiceDocument.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .description(s.getDescription())
                        .active(true)
                        .usage(ServiceUsage.builder().bookingsCount(0L).build())
                        .build());
            }
            log.info("[services] перенесена id={} name={}", s.getId(), s.getName());
            migrated++;
        }

        log.info("[services] scanned={} migrated={} skipped={}", all.size(), migrated, skipped);
    }

    private void migrateBookings() {
        var all = bookingRepo.findAll();
        int migrated = 0, skipped = 0, failed = 0;

        for (BookingRequest b : all) {
            try {
                if (firestore.collection("bookings").document(b.getId().toString()).get().get().exists()) {
                    log.info("[bookings] пропущена id={}", b.getId());
                    skipped++;
                    continue;
                }
                if (!dryRun) {
                    bookingStore.save(BookingDocument.builder()
                            .id(b.getId())
                            .userId(b.getUserId())
                            .serviceId(b.getServiceId())
                            .room(b.getRoom())
                            .bookingDate(toTimestamp(b.getBookingDate()))
                            .createDate(toTimestamp(b.getCreateDate()))
                            .updatedAt(toTimestamp(b.getCreateDate()))
                            .statusCode(b.getStatus().name())
                            .status(null)
                            .revision(1)
                            .schemaVersion(1)
                            .build());
                }
                log.info("[bookings] перенесена id={}", b.getId());
                migrated++;
            } catch (Exception e) {
                log.error("[bookings] ошибка id={}: {}", b.getId(), e.getMessage());
                failed++;
            }
        }

        log.info("[bookings] scanned={} migrated={} skipped={} failed={}", all.size(), migrated, skipped, failed);
        if (failed > 0) throw new IllegalStateException("Импорт завершился с ошибками: " + failed);
    }

    private Timestamp toTimestamp(LocalDateTime dt) {
        if (dt == null) return null;
        var instant = dt.toInstant(ZoneOffset.UTC);
        return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
    }

    // Run with application writes stopped. Reassignment is intentionally idempotent.
    private void rebuildCounters() {
        try {
            var bookings = firestore.collection("bookings").get().get().getDocuments();
            java.util.Map<Long,Long> counts = new java.util.HashMap<>();
            long maxId = 0;
            for (var booking : bookings) {
                maxId = Math.max(maxId, Long.parseLong(booking.getId()));
                counts.merge(booking.getLong("serviceId"), 1L, Long::sum);
            }
            var services = firestore.collection("services").get().get().getDocuments();
            var serviceIds = services.stream().map(s -> Long.parseLong(s.getId())).collect(java.util.stream.Collectors.toSet());
            if (!serviceIds.containsAll(counts.keySet())) throw new IllegalStateException("Есть заявки без услуги");
            for (var service : services) {
                service.getReference().update("usage.bookingsCount",
                        counts.getOrDefault(Long.parseLong(service.getId()), 0L)).get();
            }
            final long highest = maxId;
            operations.transaction(tx -> {
                var ref = firestore.collection("counters").document("bookings");
                var doc = tx.get(ref).get();
                Long current = doc.exists() ? doc.getLong("lastId") : null;
                tx.set(ref, java.util.Map.of("lastId", Math.max(highest, current == null ? 0L : current)));
                return null;
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IllegalStateException(e);
        } catch (java.util.concurrent.ExecutionException e) { throw new IllegalStateException(e); }
    }
}
