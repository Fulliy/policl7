package com.example.medicalsystem.repository;

import com.example.medicalsystem.model.DoctorWorkingHours;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DoctorWorkingHoursRepository extends JpaRepository<DoctorWorkingHours, Integer> {
    List<DoctorWorkingHours> findByDoctor_IdUserAndDayOfWeek(Integer doctorId, Integer dayOfWeek);
}
