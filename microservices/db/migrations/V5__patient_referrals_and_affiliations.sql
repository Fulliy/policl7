-- Manual migration: apply after V1-V4 to the existing PostgreSQL database.
-- Data is patient-scoped; no records are synthesized by this schema migration.

CREATE TABLE IF NOT EXISTS patient_referrals (
    id_referral serial PRIMARY KEY,
    id_patient integer NOT NULL REFERENCES users(id_user),
    referral_date date NOT NULL,
    target varchar(200) NOT NULL,
    issued_by_name varchar(150) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT ck_patient_referrals_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT uq_patient_referral_demo_key UNIQUE (id_patient, referral_date, target, issued_by_name)
);
CREATE INDEX IF NOT EXISTS ix_patient_referrals_patient_date
    ON patient_referrals (id_patient, referral_date DESC);

CREATE TABLE IF NOT EXISTS patient_affiliations (
    id_affiliation serial PRIMARY KEY,
    id_patient integer NOT NULL REFERENCES users(id_user),
    medical_center_name varchar(200) NOT NULL,
    doctor_name varchar(150) NOT NULL,
    attached_at date NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT ck_patient_affiliations_status CHECK (status IN ('ACTIVE', 'ENDED')),
    CONSTRAINT uq_patient_affiliation_demo_key UNIQUE (id_patient, medical_center_name, doctor_name, attached_at)
);
CREATE INDEX IF NOT EXISTS ix_patient_affiliations_patient_date
    ON patient_affiliations (id_patient, attached_at DESC);



SELECT
  to_regclass('public.patient_referrals') AS referrals,
  to_regclass('public.patient_affiliations') AS affiliations;
