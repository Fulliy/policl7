package com.example.medicalsystem.repository;

import com.example.medicalsystem.model.PatientAffiliation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PatientAffiliationRepository extends JpaRepository<PatientAffiliation, Integer> {
    List<PatientAffiliation> findByPatient_IdUserOrderByAttachedAtDesc(Integer patientId);

    boolean existsByPatient_IdUserAndMedicalCenterNameAndDoctorNameAndAttachedAt(
            Integer patientId, String medicalCenterName, String doctorName, LocalDate attachedAt);

    boolean existsByPatient_IdUserAndStatus(Integer patientId, PatientAffiliation.AffiliationStatus status);
}
