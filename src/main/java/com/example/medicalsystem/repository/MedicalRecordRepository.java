package com.example.medicalsystem.repository;

import com.example.medicalsystem.model.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Integer> {

    // Поиск по диагнозу
    List<MedicalRecord> findByDiagnosisContainingIgnoreCase(String diagnosis);

    // Поиск по приему
    List<MedicalRecord> findByAppointment_IdAppointment(Integer idAppointment);

    // Поиск по дате создания
    List<MedicalRecord> findByCreatedDateBetween(LocalDateTime start, LocalDateTime end);

    // Кастомный поиск
    @Query("SELECT m FROM MedicalRecord m WHERE " +
            "LOWER(m.diagnosis) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(m.treatment) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(m.recommendations) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<MedicalRecord> searchByKeyword(@Param("keyword") String keyword);
}
