package com.example.medicalsystem.repository;

import com.example.medicalsystem.model.DoctorScheduleBreak;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DoctorScheduleBreakRepository extends JpaRepository<DoctorScheduleBreak, Integer> {
    List<DoctorScheduleBreak> findByWorkingHours_Id(Integer workingHoursId);
}
