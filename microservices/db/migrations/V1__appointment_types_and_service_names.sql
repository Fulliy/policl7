ALTER TABLE appointments ADD COLUMN IF NOT EXISTS appointment_type varchar(20);

UPDATE appointments a
SET appointment_type = CASE
    WHEN a.id_doctor IS NOT NULL THEN 'DOCTOR_VISIT'
    WHEN EXISTS (SELECT 1 FROM appointment_services aps WHERE aps.id_appointment = a.id_appointment) THEN 'SERVICE'
    ELSE 'DOCTOR_VISIT'
END
WHERE a.appointment_type IS NULL;

ALTER TABLE appointments ALTER COLUMN appointment_type SET DEFAULT 'DOCTOR_VISIT';
ALTER TABLE appointments ALTER COLUMN appointment_type SET NOT NULL;

UPDATE services SET service_name = CASE id_service
    WHEN 6 THEN 'Консультация кардиолога'
    WHEN 7 THEN 'Консультация невролога'
    WHEN 8 THEN 'Консультация травматолога'
    WHEN 9 THEN 'Рентгенография'
    WHEN 10 THEN 'Консультация педиатра'
    WHEN 11 THEN 'Общий анализ мочи'
    ELSE service_name
END
WHERE id_service BETWEEN 6 AND 11;
