package com.example.medicalsystem.repository;

import com.example.medicalsystem.model.Specialty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SpecialtyRepository extends JpaRepository<Specialty, Integer> {

    // Поиск по названию специальности
    List<Specialty> findBySpecialtyNameContainingIgnoreCase(String specialtyName);

    // Кастомный поиск
    @Query("SELECT s FROM Specialty s WHERE " +
            "LOWER(s.specialtyName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(s.description) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Specialty> searchByKeyword(@Param("keyword") String keyword);
}