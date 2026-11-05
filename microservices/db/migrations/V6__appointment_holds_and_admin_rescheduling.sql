-- Manual migration: apply after V1-V5 on PostgreSQL.
ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS requires_reschedule boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS created_by_admin boolean NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS appointment_slot_holds (
    id_hold serial PRIMARY KEY,
    id_patient integer NOT NULL REFERENCES users(id_user) ON DELETE CASCADE,
    id_doctor integer NOT NULL REFERENCES users(id_user) ON DELETE CASCADE,
    appointment_date timestamp without time zone NOT NULL,
    duration_minutes integer NOT NULL CHECK (duration_minutes > 0),
    expires_at timestamp without time zone NOT NULL,
    CONSTRAINT uq_hold_doctor_slot UNIQUE (id_doctor, appointment_date),
    CONSTRAINT uq_hold_patient UNIQUE (id_patient)
);

CREATE INDEX IF NOT EXISTS ix_appointment_holds_expiry ON appointment_slot_holds (expires_at);
