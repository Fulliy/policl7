package com.example.medicalsystem.repository;

import com.example.medicalsystem.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RoleRepository extends JpaRepository<Role, Integer> {

    // Поиск по названию роли (содержит, без учета регистра)
    List<Role> findByRoleNameContainingIgnoreCase(String roleName);

    // ДОБАВЛЕНО: Поиск по специальности
    List<Role> findBySpecialty_IdSpecialty(Integer idSpecialty);

    // ДОБАВЛЕНО: Кастомный поиск по ключевому слову
    @Query("SELECT r FROM Role r WHERE " +
            "LOWER(r.roleName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(r.description) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Role> searchByKeyword(@Param("keyword") String keyword);
}
