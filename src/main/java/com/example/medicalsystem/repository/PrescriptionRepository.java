package com.example.medicalsystem.repository;

import com.example.medicalsystem.model.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, Integer> {

    // Поиск по названию лекарства
    List<Prescription> findByMedicationNameContainingIgnoreCase(String medicationName);

    // Поиск по медицинской записи
    List<Prescription> findByMedicalRecord_IdRecord(Integer idRecord);

    boolean existsByMedicalRecord_IdRecord(Integer idRecord);

    // Поиск по услуге
    List<Prescription> findByService_IdService(Integer idService);

    // Поиск по длительности курса
    List<Prescription> findByDurationDaysGreaterThanEqual(Integer days);

    // Кастомный поиск
    @Query("SELECT p FROM Prescription p WHERE " +
            "LOWER(p.medicationName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(p.dosage) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(p.frequency) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Prescription> searchByKeyword(@Param("keyword") String keyword);
}
