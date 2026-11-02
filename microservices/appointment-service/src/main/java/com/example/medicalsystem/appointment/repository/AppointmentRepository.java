package com.example.medicalsystem.appointment.repository;

import com.example.medicalsystem.appointment.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Integer> {
    List<Appointment> findByUserId(Integer userId);
    List<Appointment> findByDoctorId(Integer doctorId);
}
