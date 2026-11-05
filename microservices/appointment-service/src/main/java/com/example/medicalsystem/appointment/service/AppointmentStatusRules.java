package com.example.medicalsystem.appointment.service;

import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class AppointmentStatusRules {
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "pending", Set.of("confirmed", "cancelled", "no_show"),
            "confirmed", Set.of("cancelled", "completed", "no_show"),
            "cancelled", Set.of(), "completed", Set.of(), "no_show", Set.of());
    private AppointmentStatusRules() { }
    static boolean canTransition(String from, String to) {
        return normalize(from).equals(normalize(to)) || TRANSITIONS.getOrDefault(normalize(from), Set.of()).contains(normalize(to));
    }
    static String normalize(String status) {
        if (status == null) return "pending";
        return switch (status.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "запланирован", "запланировано" -> "pending";
            case "подтвержден", "подтверждён" -> "confirmed";
            case "отменен", "отменён" -> "cancelled";
            case "завершен", "завершён" -> "completed";
            case "пропущен" -> "no_show";
            case "pending", "confirmed", "cancelled", "completed", "no_show" -> status.trim().toLowerCase(java.util.Locale.ROOT);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Недопустимый статус записи: " + status);
        };
    }
}
