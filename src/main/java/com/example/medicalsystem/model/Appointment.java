package com.example.medicalsystem.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.JoinTable;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idAppointment;

    @NotNull(message = "Дата и время приема обязательны")
    @FutureOrPresent(message = "Дата приема не может быть в прошлом")
    private LocalDateTime appointmentDate;

    @NotBlank(message = "Статус обязателен")
    private String status;

    @Size(max = 500, message = "Жалоба должна быть не длиннее 500 символов")
    private String complaint;

    @NotNull(message = "Пациент обязателен")
    @ManyToOne
    @JoinColumn(name = "id_user")
    private User user;

    @ManyToOne
    @JoinColumn(name = "id_doctor")
    private User doctor;

    @ManyToOne
    @JoinColumn(name = "id_department")
    private Department department;

    @OneToMany(mappedBy = "appointment")
    private List<MedicalRecord> medicalRecords;

    @ManyToMany
    @JoinTable(name = "appointment_services",
            joinColumns = @JoinColumn(name = "id_appointment"),
            inverseJoinColumns = @JoinColumn(name = "id_service"))
    private List<MedicalServiceEntity> services = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @jakarta.persistence.Column(name = "appointment_type", nullable = false, length = 20)
    private AppointmentType type = AppointmentType.DOCTOR_VISIT;

    public Appointment() {
    }

    public Appointment(Integer idAppointment, LocalDateTime appointmentDate, String status,
                       String complaint, User user, Department department) {
        this.idAppointment = idAppointment;
        this.appointmentDate = appointmentDate;
        this.status = status;
        this.complaint = complaint;
        this.user = user;
        this.department = department;
    }

    public Integer getIdAppointment() { return idAppointment; }
    public void setIdAppointment(Integer idAppointment) { this.idAppointment = idAppointment; }
    public LocalDateTime getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDateTime appointmentDate) { this.appointmentDate = appointmentDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getComplaint() { return complaint; }
    public void setComplaint(String complaint) { this.complaint = complaint; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public User getDoctor() { return doctor; }
    public void setDoctor(User doctor) { this.doctor = doctor; }
    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }
    public List<MedicalRecord> getMedicalRecords() { return medicalRecords; }
    public void setMedicalRecords(List<MedicalRecord> medicalRecords) { this.medicalRecords = medicalRecords; }
    public List<MedicalServiceEntity> getServices() { return services; }
    public void setServices(List<MedicalServiceEntity> services) { this.services = services; }
    public AppointmentType getType() { return type; }
    public void setType(AppointmentType type) { this.type = type; }
}
