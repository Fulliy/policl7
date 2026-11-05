package com.example.medicalsystem.appointment.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Entity
@Table(name = "appointments")
public class Appointment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_appointment")
    private Integer idAppointment;
    @NotNull @FutureOrPresent
    @Column(name = "appointment_date", nullable = false)
    private LocalDateTime appointmentDate;
    @NotBlank private String status;
    @Size(max = 500) private String complaint;
    @NotNull @Column(name = "id_user", nullable = false) private Integer userId;
    @Column(name = "id_doctor") private Integer doctorId;
    @Column(name = "id_department") private Integer departmentId;
    @Enumerated(EnumType.STRING)
    @Column(name = "appointment_type", nullable = false, length = 20)
    private AppointmentType type = AppointmentType.DOCTOR_VISIT;
    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes = 30;
    public Appointment() { }
    public Integer getIdAppointment() { return idAppointment; }
    public LocalDateTime getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDateTime value) { appointmentDate = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getComplaint() { return complaint; }
    public void setComplaint(String value) { complaint = value; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer value) { userId = value; }
    public Integer getDoctorId() { return doctorId; }
    public void setDoctorId(Integer value) { doctorId = value; }
    public Integer getDepartmentId() { return departmentId; }
    public void setDepartmentId(Integer value) { departmentId = value; }
    public AppointmentType getType() { return type; }
    public void setType(AppointmentType value) { type = value; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer value) { durationMinutes = value; }
}
