package com.example.medicalsystem.model;

public enum AppointmentStatus {
    pending, confirmed, cancelled, completed, no_show;

    public boolean occupiesSlot() {
        return this == pending || this == confirmed;
    }

    public boolean canTransitionTo(AppointmentStatus next) {
        return switch (this) {
            case pending -> next == confirmed || next == cancelled || next == no_show || next == completed;
            case confirmed -> next == cancelled || next == completed || next == no_show;
            case cancelled, completed, no_show -> false;
        };
    }

    public static AppointmentStatus parse(String value) {
        if (value == null || value.isBlank()) return pending;
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "pending", "запланирован", "запланировано" -> pending;
            case "confirmed", "подтвержден", "подтверждён" -> confirmed;
            case "cancelled", "отменен", "отменён" -> cancelled;
            case "completed", "завершен", "завершён" -> completed;
            case "no_show", "пропущен" -> no_show;
            default -> throw new IllegalArgumentException("Недопустимый статус записи: " + value);
        };
    }
}
