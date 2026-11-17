package ru.universitybooking.scylla;

import java.util.List;

public record EventPage(List<EventRecord> items, String nextCursor) {}
