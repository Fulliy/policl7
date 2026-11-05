package com.example.medicalsystem.repository;

import com.example.medicalsystem.model.PatientReferral;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PatientReferralRepository extends JpaRepository<PatientReferral, Integer> {
    List<PatientReferral> findByPatient_IdUserOrderByReferralDateDesc(Integer patientId);

    boolean existsByPatient_IdUserAndReferralDateAndTargetAndIssuedByName(
            Integer patientId, LocalDate referralDate, String target, String issuedByName);
}
