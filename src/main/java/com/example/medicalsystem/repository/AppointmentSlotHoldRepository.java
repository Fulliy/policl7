package com.example.medicalsystem.repository;

import com.example.medicalsystem.model.AppointmentSlotHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentSlotHoldRepository extends JpaRepository<AppointmentSlotHold, Integer> {
    Optional<AppointmentSlotHold> findByIdAndPatient_IdUser(Integer id, Integer patientId);
    List<AppointmentSlotHold> findByDoctor_IdUserAndExpiresAtAfter(Integer doctorId, LocalDateTime now);
    Optional<AppointmentSlotHold> findByPatient_IdUser(Integer patientId);

    @Modifying
    @Query("delete from AppointmentSlotHold hold where hold.expiresAt <= :now")
    int deleteExpired(@Param("now") LocalDateTime now);

    void deleteByPatient_IdUser(Integer patientId);
}
