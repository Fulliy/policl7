-- Manual migration: apply after V1 and V2 against the existing PostgreSQL database.
-- Hibernate remains in validate mode; this script intentionally does not delete data.

-- V1 may already have created this column. IF NOT EXISTS keeps V3 safe in either case;
-- only NULL values are backfilled, existing appointment types are preserved.
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS appointment_type varchar(20);
UPDATE appointments a
SET appointment_type = CASE
    WHEN a.id_doctor IS NOT NULL THEN 'DOCTOR_VISIT'
    WHEN EXISTS (SELECT 1 FROM appointment_services aps WHERE aps.id_appointment = a.id_appointment) THEN 'SERVICE'
    ELSE 'DOCTOR_VISIT'
END
WHERE a.appointment_type IS NULL;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM appointments WHERE appointment_type NOT IN ('DOCTOR_VISIT', 'SERVICE')) THEN
        RAISE EXCEPTION 'Обнаружен неизвестный тип записи; проверьте appointment_type перед V3';
    END IF;
END $$;
ALTER TABLE appointments ALTER COLUMN appointment_type SET DEFAULT 'DOCTOR_VISIT';
ALTER TABLE appointments ALTER COLUMN appointment_type SET NOT NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM appointments WHERE lower(status) NOT IN (
        'запланирован','запланировано','подтвержден','подтверждён','отменен','отменён',
        'завершен','завершён','пропущен','pending','confirmed','cancelled','completed','no_show')) THEN
        RAISE EXCEPTION 'Обнаружен неизвестный статус записи; проверьте данные перед миграцией V3';
    END IF;
END $$;

-- Remove indexes whose legacy predicates use the old localized status values
-- before canonicalizing statuses, otherwise the UPDATE can trip those indexes.
DROP INDEX IF EXISTS uq_active_doctor_appointment_slot;
DROP INDEX IF EXISTS uq_active_patient_appointment_slot;

UPDATE appointments
SET status = CASE lower(status)
    WHEN 'запланирован' THEN 'pending'
    WHEN 'запланировано' THEN 'pending'
    WHEN 'подтвержден' THEN 'confirmed'
    WHEN 'подтверждён' THEN 'confirmed'
    WHEN 'отменен' THEN 'cancelled'
    WHEN 'отменён' THEN 'cancelled'
    WHEN 'завершен' THEN 'completed'
    WHEN 'завершён' THEN 'completed'
    WHEN 'пропущен' THEN 'no_show'
    WHEN 'pending' THEN 'pending'
    WHEN 'confirmed' THEN 'confirmed'
    WHEN 'cancelled' THEN 'cancelled'
    WHEN 'completed' THEN 'completed'
    WHEN 'no_show' THEN 'no_show'
    ELSE 'pending'
END;

ALTER TABLE appointments ALTER COLUMN status SET DEFAULT 'pending';
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS duration_minutes integer NOT NULL DEFAULT 30;
ALTER TABLE services ADD COLUMN IF NOT EXISTS duration_minutes integer NOT NULL DEFAULT 30;
ALTER TABLE services ADD COLUMN IF NOT EXISTS service_type varchar(20) NOT NULL DEFAULT 'PRIMARY';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='appointments'::regclass AND conname='ck_appointments_status') THEN
        ALTER TABLE appointments ADD CONSTRAINT ck_appointments_status
            CHECK (status IN ('pending', 'confirmed', 'cancelled', 'completed', 'no_show'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='appointments'::regclass AND conname='ck_appointments_type') THEN
        ALTER TABLE appointments ADD CONSTRAINT ck_appointments_type
            CHECK (appointment_type IN ('DOCTOR_VISIT', 'SERVICE'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='appointments'::regclass AND conname='ck_appointments_duration_positive') THEN
        ALTER TABLE appointments ADD CONSTRAINT ck_appointments_duration_positive CHECK (duration_minutes > 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='services'::regclass AND conname='ck_services_duration_positive') THEN
        ALTER TABLE services ADD CONSTRAINT ck_services_duration_positive CHECK (duration_minutes BETWEEN 5 AND 480);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='services'::regclass AND conname='ck_services_type') THEN
        ALTER TABLE services ADD CONSTRAINT ck_services_type CHECK (service_type IN ('PRIMARY', 'REPEAT', 'PROCEDURE'));
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM appointments WHERE status IN ('pending','confirmed')
        GROUP BY id_user, appointment_date HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Активные записи пациента на точное время дублируются; устраните их перед V3';
    END IF;
    IF EXISTS (SELECT 1 FROM appointments WHERE id_doctor IS NOT NULL AND appointment_type='DOCTOR_VISIT'
        AND status IN ('pending','confirmed') GROUP BY id_doctor, appointment_date HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Слоты врача дублируются; устраните дубли перед V3';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_active_doctor_appointment_slot
    ON appointments (id_doctor, appointment_date)
    WHERE id_doctor IS NOT NULL AND appointment_type='DOCTOR_VISIT' AND status IN ('pending','confirmed');
CREATE UNIQUE INDEX IF NOT EXISTS uq_active_patient_appointment_slot
    ON appointments (id_user, appointment_date)
    WHERE status IN ('pending','confirmed');
CREATE INDEX IF NOT EXISTS ix_active_doctor_appointments_date
    ON appointments (id_doctor, appointment_date) WHERE status IN ('pending','confirmed');
CREATE INDEX IF NOT EXISTS ix_patient_no_show_date
    ON appointments (id_user, appointment_date) WHERE status='no_show';

CREATE TABLE IF NOT EXISTS doctor_working_hours (
    id_working_hours serial PRIMARY KEY,
    id_doctor integer NOT NULL REFERENCES users(id_user),
    day_of_week integer NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    week_parity varchar(8) NULL CHECK (week_parity IN ('EVEN','ODD')),
    start_time time NOT NULL,
    end_time time NOT NULL,
    CONSTRAINT ck_working_hours_range CHECK (start_time < end_time)
);
CREATE INDEX IF NOT EXISTS ix_doctor_working_hours_lookup
    ON doctor_working_hours (id_doctor, day_of_week, week_parity);

CREATE TABLE IF NOT EXISTS doctor_schedule_breaks (
    id_schedule_break serial PRIMARY KEY,
    id_working_hours integer NOT NULL REFERENCES doctor_working_hours(id_working_hours) ON DELETE CASCADE,
    start_time time NOT NULL,
    end_time time NOT NULL,
    CONSTRAINT ck_schedule_break_range CHECK (start_time < end_time)
);

CREATE TABLE IF NOT EXISTS doctor_schedule_exceptions (
    id_schedule_exception serial PRIMARY KEY,
    id_doctor integer NOT NULL REFERENCES users(id_user),
    start_date date NOT NULL,
    end_date date NOT NULL,
    exception_type varchar(20) NOT NULL CHECK (exception_type IN ('DAY_OFF','HOLIDAY','VACATION','SICK_LEAVE')),
    description varchar(255),
    CONSTRAINT ck_schedule_exception_range CHECK (start_date <= end_date)
);
CREATE INDEX IF NOT EXISTS ix_doctor_schedule_exceptions_dates
    ON doctor_schedule_exceptions (id_doctor, start_date, end_date);

CREATE TABLE IF NOT EXISTS patient_booking_restrictions (
    id_user integer PRIMARY KEY REFERENCES users(id_user),
    blocked boolean NOT NULL DEFAULT false,
    no_show_block_overridden boolean NOT NULL DEFAULT false,
    reason varchar(500),
    updated_at timestamp NOT NULL DEFAULT current_timestamp
);
