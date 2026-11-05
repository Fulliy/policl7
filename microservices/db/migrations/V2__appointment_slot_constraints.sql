-- Requires V1__appointment_types_and_service_names.sql to be applied first.
-- Active means status is not cancelled, completed, or missed.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM appointments
        WHERE lower(status) NOT IN ('отменен', 'отменён', 'завершен', 'завершён', 'пропущен')
        GROUP BY id_user, appointment_date HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Найдены дубли активных записей пациента на одно время; устраните дубли перед миграцией V2';
    END IF;
    IF EXISTS (
        SELECT 1 FROM appointments
        WHERE id_doctor IS NOT NULL AND appointment_type = 'DOCTOR_VISIT'
          AND lower(status) NOT IN ('отменен', 'отменён', 'завершен', 'завершён', 'пропущен')
        GROUP BY id_doctor, appointment_date HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Найдены дубли активных слотов врача; устраните дубли перед миграцией V2';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_active_doctor_appointment_slot
    ON appointments (id_doctor, appointment_date)
    WHERE id_doctor IS NOT NULL
      AND appointment_type = 'DOCTOR_VISIT'
      AND lower(status) NOT IN ('отменен', 'отменён', 'завершен', 'завершён', 'пропущен');

CREATE UNIQUE INDEX IF NOT EXISTS uq_active_patient_appointment_slot
    ON appointments (id_user, appointment_date)
    WHERE lower(status) NOT IN ('отменен', 'отменён', 'завершен', 'завершён', 'пропущен');
