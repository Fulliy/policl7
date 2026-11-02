package com.example.medicalsystem.repository;

import com.example.medicalsystem.model.MedicalServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ServiceRepository extends JpaRepository<MedicalServiceEntity, Integer> {

    List<MedicalServiceEntity> findByServiceNameContainingIgnoreCase(String serviceName);

    @Query("SELECT s FROM MedicalServiceEntity s WHERE " +
            "LOWER(s.serviceName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(s.description) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<MedicalServiceEntity> searchByKeyword(@Param("keyword") String keyword);
}