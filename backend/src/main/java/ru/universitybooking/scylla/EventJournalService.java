package ru.universitybooking.scylla;

import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class EventJournalService {

    private final EventJournalRepository repo;

    public EventJournalService(EventJournalRepository repo) {
        this.repo = repo;
    }

    public void recordAsync(EventOperation operation, EventResult result,
                            Long bookingId, String errorCode) {
        EventRecord event = new EventRecord(
                UUID.randomUUID(),
                Instant.now(),
                operation,
                result,
                bookingId,
                errorCode
        );
        Thread.ofVirtual().start(() -> repo.save(event));
    }

    public EventPage getByResult(EventResult result, Instant from, Instant to,
                                  int limit, String cursorStr) {
        return query(limit, cursorStr, from, to,
                (dayUtc, pageSize, pagingState) ->
                        repo.queryByResult(result.name(), dayUtc, pageSize, pagingState));
    }

    public EventPage getByOperation(EventOperation operation, Instant from, Instant to,
                                     int limit, String cursorStr) {
        return query(limit, cursorStr, from, to,
                (dayUtc, pageSize, pagingState) ->
                        repo.queryByOperation(operation.name(), dayUtc, pageSize, pagingState));
    }

    public EventPage getByBooking(long bookingId, Instant from, Instant to,
                                   int limit, String cursorStr) {
        return query(limit, cursorStr, from, to,
                (dayUtc, pageSize, pagingState) ->
                        repo.queryByBooking(bookingId, dayUtc, pageSize, pagingState));
    }

    private EventPage query(int limit, String cursorStr,
                             Instant from, Instant to,
                             DayQueryFn queryFn) {
        validateRange(from, to, limit);

        LocalDate toDay   = LocalDate.ofInstant(to, ZoneOffset.UTC);
        LocalDate fromDay = LocalDate.ofInstant(from, ZoneOffset.UTC);

        EventCursor cursor = cursorStr != null ? EventCursor.decode(cursorStr) : null;
        LocalDate startDay = cursor != null ? LocalDate.parse(cursor.dayUtc()) : toDay;

        List<EventRecord> collected = new ArrayList<>();
        int remaining = limit;
        ByteBuffer pendingState = cursor != null ? toPagingState(cursor) : null;

        LocalDate day = startDay;
        while (!day.isBefore(fromDay) && remaining > 0) {
            String dayUtc = day.toString();

            EventJournalRepository.PageResult page =
                    queryFn.query(dayUtc, remaining, pendingState);

            collected.addAll(page.items());
            remaining -= page.items().size();

            ByteBuffer nextState = page.nextPagingState();

            if (remaining <= 0) {
                String nextCursor = nextState != null
                        ? EventCursor.of(dayUtc, nextState).encode()
                        : null;
                if (nextCursor == null && !day.minusDays(1).isBefore(fromDay)) {
                    nextCursor = EventCursor.of(day.minusDays(1).toString(), null).encode();
                }
                return new EventPage(collected, nextCursor);
            }

            pendingState = null;
            day = day.minusDays(1);
        }

        return new EventPage(collected, null);
    }

    private static ByteBuffer toPagingState(EventCursor cursor) {
        if (cursor.pagingStateBytes() == null) return null;
        return ByteBuffer.wrap(cursor.pagingStateBytes());
    }

    private static void validateRange(Instant from, Instant to, int limit) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Параметры from и to обязательны");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from не может быть позже to");
        }
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("limit должен быть от 1 до 500");
        }
    }

    @FunctionalInterface
    private interface DayQueryFn {
        EventJournalRepository.PageResult query(String dayUtc, int pageSize, ByteBuffer pagingState);
    }
}
