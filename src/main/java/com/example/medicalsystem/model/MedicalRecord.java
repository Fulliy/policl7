package com.example.medicalsystem.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "medical_records")
public class MedicalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idRecord;

    @NotBlank(message = "Диагноз обязателен")
    @Size(min = 2, max = 500, message = "Диагноз должен быть от 2 до 500 символов")
    private String diagnosis;

    @Size(max = 1000, message = "Лечение должно быть не длиннее 1000 символов")
    private String treatment;

    @Size(max = 500, message = "Рекомендации должны быть не длиннее 500 символов")
    private String recommendations;

    @NotNull(message = "Дата создания обязательна")
    @PastOrPresent(message = "Дата создания не может быть в будущем")
    private LocalDateTime createdDate;

    @NotNull(message = "Прием обязателен")
    @ManyToOne
    @JoinColumn(name = "id_appointment")
    private Appointment appointment;

    @OneToMany(mappedBy = "medicalRecord")
    private List<Prescription> prescriptions;

    public MedicalRecord() {
    }

    public MedicalRecord(Integer idRecord, String diagnosis, String treatment,
                         String recommendations, LocalDateTime createdDate, Appointment appointment) {
        this.idRecord = idRecord;
        this.diagnosis = diagnosis;
        this.treatment = treatment;
        this.recommendations = recommendations;
        this.createdDate = createdDate;
        this.appointment = appointment;
    }

    public Integer getIdRecord() { return idRecord; }
    public void setIdRecord(Integer idRecord) { this.idRecord = idRecord; }
    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }
    public String getTreatment() { return treatment; }
    public void setTreatment(String treatment) { this.treatment = treatment; }
    public String getRecommendations() { return recommendations; }
    public void setRecommendations(String recommendations) { this.recommendations = recommendations; }
    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }
    public Appointment getAppointment() { return appointment; }
    public void setAppointment(Appointment appointment) { this.appointment = appointment; }
    public List<Prescription> getPrescriptions() { return prescriptions; }
    public void setPrescriptions(List<Prescription> prescriptions) { this.prescriptions = prescriptions; }
}
