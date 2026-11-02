package com.example.medicalsystem.repository;

import com.example.medicalsystem.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Integer> {

    // Поиск по статусу
    List<Appointment> findByStatus(String status);

    // Поиск по пациенту
    List<Appointment> findByUser_IdUser(Integer idUser);

    List<Appointment> findByDoctor_IdUser(Integer doctorId);

    List<Appointment> findByDoctor_IdUserAndAppointmentDate(Integer doctorId, LocalDateTime appointmentDate);

    // Поиск по отделению
    List<Appointment> findByDepartment_IdDepartment(Integer idDepartment);

    // Поиск по дате (после указанной)
    List<Appointment> findByAppointmentDateAfter(LocalDateTime date);

    // Поиск по дате (до указанной)
    List<Appointment> findByAppointmentDateBefore(LocalDateTime date);

    // Поиск по диапазону дат
    List<Appointment> findByAppointmentDateBetween(LocalDateTime start, LocalDateTime end);

    // Кастомный поиск
    @Query("SELECT a FROM Appointment a WHERE " +
            "LOWER(a.status) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(a.complaint) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Appointment> searchByKeyword(@Param("keyword") String keyword);
}
