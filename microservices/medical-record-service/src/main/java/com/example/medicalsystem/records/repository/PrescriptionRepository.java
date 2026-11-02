package com.example.medicalsystem.records.repository;

import com.example.medicalsystem.records.model.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PrescriptionRepository extends JpaRepository<Prescription, Integer> {
    List<Prescription> findByMedicalRecordId(Integer medicalRecordId);
}
