package ru.universitybooking.dto;

import java.util.List;

public record BookingPage<T>(List<T> items, String nextCursor) {}