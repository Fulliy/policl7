package com.example.medicalsystem.records.repository;

import com.example.medicalsystem.records.model.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Integer> { }
