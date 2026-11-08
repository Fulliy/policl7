package com.example.medicalsystem.repository;

import com.example.medicalsystem.model.AppointmentGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AppointmentGoalRepository extends JpaRepository<AppointmentGoal, Integer> {
    java.util.Optional<AppointmentGoal> findFirstByNameIgnoreCase(String name);
    List<AppointmentGoal> findByActiveTrueOrderBySortOrderAscNameAsc();
    List<AppointmentGoal> findAllByOrderBySortOrderAscNameAsc();
}
