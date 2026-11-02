package com.example.medicalsystem.repository;

import com.example.medicalsystem.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Integer> {

    // Поиск по названию отделения
    List<Department> findByDepartmentNameContainingIgnoreCase(String departmentName);

    // Поиск по этажу
    List<Department> findByFloor(Integer floor);

    // Кастомный поиск
    @Query("SELECT d FROM Department d WHERE " +
            "LOWER(d.departmentName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "CAST(d.floor AS string) LIKE CONCAT('%', :keyword, '%') OR " +
            "LOWER(d.roomNumber) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Department> searchByKeyword(@Param("keyword") String keyword);
}