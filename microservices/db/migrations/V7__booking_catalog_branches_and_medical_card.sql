-- Manual migration: apply after V1-V6. Existing appointments and patient data are preserved.
ALTER TABLE users ADD COLUMN IF NOT EXISTS on_duty_orvi boolean NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS clinic_branches (
    id_branch serial PRIMARY KEY,
    name varchar(120) NOT NULL UNIQUE,
    address varchar(255) NOT NULL,
    phone varchar(30)
);
CREATE TABLE IF NOT EXISTS doctor_branches (
    id_doctor integer NOT NULL REFERENCES users(id_user) ON DELETE CASCADE,
    id_branch integer NOT NULL REFERENCES clinic_branches(id_branch) ON DELETE CASCADE,
    PRIMARY KEY (id_doctor, id_branch)
);
CREATE TABLE IF NOT EXISTS appointment_goals (
    id_goal serial PRIMARY KEY,
    name varchar(120) NOT NULL UNIQUE,
    icon_key varchar(40) NOT NULL,
    goal_group varchar(30) NOT NULL CHECK (goal_group IN ('POLYCLINIC','OTHER_INSTITUTIONS')),
    scenario_type varchar(30) NOT NULL CHECK (scenario_type IN ('ORVI_DUTY','SPECIALTY','DOCTORS','PAID_SERVICE','EXTERNAL')),
    sort_order integer NOT NULL DEFAULT 0,
    paid boolean NOT NULL DEFAULT false,
    active boolean NOT NULL DEFAULT true,
    description varchar(500),
    external_url varchar(500)
);
ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS id_branch integer REFERENCES clinic_branches(id_branch),
    ADD COLUMN IF NOT EXISTS id_goal integer REFERENCES appointment_goals(id_goal);
CREATE TABLE IF NOT EXISTS appointment_goal_specialties (
    id_goal integer NOT NULL REFERENCES appointment_goals(id_goal) ON DELETE CASCADE,
    id_specialty integer NOT NULL REFERENCES specialties(id_specialty),
    PRIMARY KEY (id_goal, id_specialty)
);
CREATE INDEX IF NOT EXISTS ix_doctor_branches_branch ON doctor_branches(id_branch, id_doctor);
CREATE INDEX IF NOT EXISTS ix_appointment_goals_active_order ON appointment_goals(active, sort_order);

-- Migrate each current doctor's legacy department relationship into the new explicit doctor relation.
CREATE TABLE IF NOT EXISTS doctor_departments (
    id_doctor integer NOT NULL REFERENCES users(id_user) ON DELETE CASCADE,
    id_department integer NOT NULL REFERENCES departments(id_department) ON DELETE CASCADE,
    PRIMARY KEY (id_doctor, id_department)
);
INSERT INTO doctor_departments(id_doctor, id_department)
SELECT u.id_user, u.id_department FROM users u
JOIN user_role ur ON ur.user_id=u.id_user AND ur.roles='DOCTOR'
WHERE u.id_department IS NOT NULL
ON CONFLICT DO NOTHING;
