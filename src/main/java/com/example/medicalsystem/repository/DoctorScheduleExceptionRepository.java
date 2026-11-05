package com.example.medicalsystem.repository;

import com.example.medicalsystem.model.DoctorScheduleException;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface DoctorScheduleExceptionRepository extends JpaRepository<DoctorScheduleException, Integer> {
    List<DoctorScheduleException> findByDoctor_IdUserAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Integer doctorId, LocalDate end, LocalDate start);
}
