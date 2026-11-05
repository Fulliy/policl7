-- Hibernate maps DoctorWorkingHours.dayOfWeek as Java Integer / SQL INTEGER.
-- V3 initially created SMALLINT, so widen the column without changing its values.
DO $$
DECLARE
    current_type text;
BEGIN
    SELECT data_type INTO current_type
    FROM information_schema.columns
    WHERE table_schema = current_schema()
      AND table_name = 'doctor_working_hours'
      AND column_name = 'day_of_week';

    IF current_type IS NULL THEN
        RAISE EXCEPTION 'Не найдена колонка public.doctor_working_hours.day_of_week; проверьте, что V3 применена';
    ELSIF current_type = 'smallint' THEN
        ALTER TABLE doctor_working_hours
            ALTER COLUMN day_of_week TYPE integer USING day_of_week::integer;
    ELSIF current_type <> 'integer' THEN
        RAISE EXCEPTION 'Ожидался тип smallint или integer у doctor_working_hours.day_of_week, найден: %', current_type;
    END IF;
END $$;


SELECT data_type
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'doctor_working_hours'
  AND column_name = 'day_of_week';