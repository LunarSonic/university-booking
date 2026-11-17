package ru.universitybooking.scylla;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;


public record EventCursor(String dayUtc, byte[] pagingStateBytes) {

    private static final String VERSION = "1";
    private static final String NONE = "NONE";

    public String encode() {
        String pagingHex = pagingStateBytes == null ? NONE : HexFormat.of().formatHex(pagingStateBytes);
        String raw = VERSION + "|" + dayUtc + "|" + pagingHex;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static EventCursor decode(String encoded) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 3);
            if (parts.length != 3 || !VERSION.equals(parts[0])) {
                throw new IllegalArgumentException("Некорректный cursor");
            }
            String day = parts[1];
            if (!day.matches("\\d{4}-\\d{2}-\\d{2}")) {
                throw new IllegalArgumentException("Некорректный день в cursor: " + day);
            }
            byte[] pagingBytes = NONE.equals(parts[2]) ? null : HexFormat.of().parseHex(parts[2]);
            return new EventCursor(day, pagingBytes);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Некорректный cursor: " + e.getMessage());
        }
    }

    public static EventCursor of(String dayUtc, ByteBuffer pagingState) {
        if (pagingState == null) {
            return new EventCursor(dayUtc, null);
        }
        byte[] bytes = new byte[pagingState.remaining()];
        pagingState.duplicate().get(bytes);
        return new EventCursor(dayUtc, bytes);
    }
}
