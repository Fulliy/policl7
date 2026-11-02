package com.example.medicalsystem.appointment.repository;

import com.example.medicalsystem.appointment.model.MedicalServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalServiceRepository extends JpaRepository<MedicalServiceEntity, Integer> { }
