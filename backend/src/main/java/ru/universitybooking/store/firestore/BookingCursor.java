package ru.universitybooking.store.firestore;

import ru.universitybooking.dto.BookingSearchRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HexFormat;

public record BookingCursor(Instant createdAt, String documentId) {

    public String encode(BookingSearchRequest request) {
        String value = "1|" + createdAt + "|" + documentId + "|" + fingerprint(request);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static BookingCursor decode(String value, BookingSearchRequest request) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value),
                    StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 4 || !parts[0].equals("1")
                    || !parts[3].equals(fingerprint(request))) {
                throw new IllegalArgumentException();
            }
            long id = Long.parseLong(parts[2]);
            if (id < 1 || !Long.toString(id).equals(parts[2])) {
                throw new IllegalArgumentException();
            }
            Instant date = Instant.parse(parts[1]);
            if (date.isBefore(request.createdFrom())
                    || !date.isBefore(request.createdTo())) {
                throw new IllegalArgumentException();
            }
            return new BookingCursor(date, parts[2]);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Некорректный cursor или изменены условия поиска");
        }
    }

    private static String fingerprint(BookingSearchRequest request) {
        String filters = request.status() + "|" + request.createdFrom()
                + "|" + request.createdTo() + "|" + request.userId()
                + "|" + request.serviceId() + "|" + request.needsProjector()
                + "|" + request.direction();
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(filters.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }
}