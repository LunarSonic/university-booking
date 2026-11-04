package ru.universitybooking.controller;
import com.google.cloud.firestore.Firestore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {
    private final Firestore firestore;
    public record Bucket(String start, long changesCount) {}
    public record Analytics(String interval, String timezone, List<Bucket> items) {}
    @GetMapping("/booking-changes")
    public Analytics changes(@RequestParam LocalDate from, @RequestParam LocalDate to,
            @RequestParam(defaultValue="day") String interval) {
        if (!interval.equals("day") || !from.isBefore(to)
                || ChronoUnit.DAYS.between(from, to) > 366)
            throw new IllegalArgumentException("Нужен interval=day и период от 1 до 366 суток [from,to)");
        try {
            var docs = firestore.collection("bookingChangeDaily")
                    .whereGreaterThanOrEqualTo("day", from.toString())
                    .whereLessThan("day", to.toString()).get().get().getDocuments();
            Map<String, Long> counts = new HashMap<>();
            for (var doc : docs) counts.put(doc.getString("day"), doc.getLong("changesCount"));
            var items = from.datesUntil(to).map(day -> new Bucket(day.toString(),
                    counts.getOrDefault(day.toString(), 0L))).toList();
            return new Analytics("day", "UTC", items);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("Чтение аналитики прервано", e);
        } catch (ExecutionException e) { throw new IllegalStateException("Ошибка чтения аналитики", e); }
    }
}
