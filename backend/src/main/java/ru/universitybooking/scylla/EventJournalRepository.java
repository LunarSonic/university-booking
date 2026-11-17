package ru.universitybooking.scylla;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Repository
public class EventJournalRepository {

    private static final Logger log = LoggerFactory.getLogger(EventJournalRepository.class);

    private final CqlSession session;
    private final String keyspace;

    private PreparedStatement insertByResult;
    private PreparedStatement insertByOperation;
    private PreparedStatement insertByBooking;

    private PreparedStatement selectByResult;
    private PreparedStatement selectByOperation;
    private PreparedStatement selectByBooking;

    public EventJournalRepository(CqlSession session, @Value("${scylla.keyspace}") String keyspace) {
        this.session = session;
        this.keyspace = keyspace;
    }

    @PostConstruct
    void prepareStatements() {
        insertByResult = session.prepare(
                "INSERT INTO " + keyspace + ".events_by_result_day " +
                "(result, day_utc, occurred_at, event_id, operation, booking_id, error_code) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)"
        );
        insertByOperation = session.prepare(
                "INSERT INTO " + keyspace + ".events_by_operation_day " +
                "(operation, day_utc, occurred_at, event_id, result, booking_id, error_code) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)"
        );
        insertByBooking = session.prepare(
                "INSERT INTO " + keyspace + ".events_by_booking_day " +
                "(booking_id, day_utc, occurred_at, event_id, operation, result, error_code) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)"
        );

        selectByResult = session.prepare(
                "SELECT event_id, occurred_at, operation, result, booking_id, error_code " +
                "FROM " + keyspace + ".events_by_result_day " +
                "WHERE result = ? AND day_utc = ?"
        );
        selectByOperation = session.prepare(
                "SELECT event_id, occurred_at, operation, result, booking_id, error_code " +
                "FROM " + keyspace + ".events_by_operation_day " +
                "WHERE operation = ? AND day_utc = ?"
        );
        selectByBooking = session.prepare(
                "SELECT event_id, occurred_at, operation, result, booking_id, error_code " +
                "FROM " + keyspace + ".events_by_booking_day " +
                "WHERE booking_id = ? AND day_utc = ?"
        );
        log.info("ScyllaDB prepared statements ready");
    }

    public void save(EventRecord event) {
        String day = dayUtc(event.occurredAt());
        writeWithRetry("events_by_result_day", () -> {
            BoundStatement stmt = insertByResult.bind(
                    event.result().name(), day, event.occurredAt(),
                    event.eventId(), event.operation().name(),
                    event.bookingId(), event.errorCode()
            ).setConsistencyLevel(DefaultConsistencyLevel.ONE);
            session.execute(stmt);
        });
        writeWithRetry("events_by_operation_day", () -> {
            BoundStatement stmt = insertByOperation.bind(
                    event.operation().name(), day, event.occurredAt(),
                    event.eventId(), event.result().name(),
                    event.bookingId(), event.errorCode()
            ).setConsistencyLevel(DefaultConsistencyLevel.ONE);
            session.execute(stmt);
        });
        if (event.bookingId() != null) {
            writeWithRetry("events_by_booking_day", () -> {
                BoundStatement stmt = insertByBooking.bind(
                        event.bookingId(), day, event.occurredAt(),
                        event.eventId(), event.operation().name(),
                        event.result().name(), event.errorCode()
                ).setConsistencyLevel(DefaultConsistencyLevel.ONE);
                session.execute(stmt);
            });
        }
    }

    public PageResult queryByResult(String result, String dayUtc,
                                    int pageSize, ByteBuffer pagingState) {
        BoundStatement stmt = selectByResult.bind(result, dayUtc)
                .setConsistencyLevel(DefaultConsistencyLevel.QUORUM)
                .setPageSize(pageSize);
        if (pagingState != null) {
            stmt = stmt.setPagingState(pagingState);
        }
        return fetchPage(session.execute(stmt), pageSize);
    }

    public PageResult queryByOperation(String operation, String dayUtc,
                                       int pageSize, ByteBuffer pagingState) {
        BoundStatement stmt = selectByOperation.bind(operation, dayUtc)
                .setConsistencyLevel(DefaultConsistencyLevel.QUORUM)
                .setPageSize(pageSize);
        if (pagingState != null) {
            stmt = stmt.setPagingState(pagingState);
        }
        return fetchPage(session.execute(stmt), pageSize);
    }

    public PageResult queryByBooking(long bookingId, String dayUtc,
                                     int pageSize, ByteBuffer pagingState) {
        BoundStatement stmt = selectByBooking.bind(bookingId, dayUtc)
                .setConsistencyLevel(DefaultConsistencyLevel.QUORUM)
                .setPageSize(pageSize);
        if (pagingState != null) {
            stmt = stmt.setPagingState(pagingState);
        }
        return fetchPage(session.execute(stmt), pageSize);
    }

    private PageResult fetchPage(ResultSet rs, int pageSize) {
        List<EventRecord> items = new ArrayList<>();
        int available = rs.getAvailableWithoutFetching();
        int take = Math.min(available, pageSize);
        for (int i = 0; i < take; i++) {
            items.add(toRecord(rs.one()));
        }
        ByteBuffer nextState = rs.getExecutionInfo().getPagingState();
        return new PageResult(items, nextState);
    }

    private static EventRecord toRecord(Row row) {
        return new EventRecord(
                row.getUuid("event_id"),
                row.getInstant("occurred_at"),
                EventOperation.valueOf(row.getString("operation")),
                EventResult.valueOf(row.getString("result")),
                row.isNull("booking_id") ? null : row.getLong("booking_id"),
                row.getString("error_code")
        );
    }

    private static String dayUtc(Instant ts) {
        return LocalDate.ofInstant(ts, ZoneOffset.UTC).toString();
    }

    private static void writeWithRetry(String table, Runnable action) {
        int maxAttempts = 3;
        Exception last = null;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                action.run();
                return;
            } catch (Exception e) {
                last = e;
                log.warn("Ошибка записи в {} (попытка {}/{}): {}", table, i + 1, maxAttempts, e.getMessage());
                if (i < maxAttempts - 1) {
                    try { Thread.sleep(100L * (i + 1)); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.error("Не удалось записать событие в {} после {} попыток", table, maxAttempts, last);
    }

    public record PageResult(List<EventRecord> items, ByteBuffer nextPagingState) {}
}
