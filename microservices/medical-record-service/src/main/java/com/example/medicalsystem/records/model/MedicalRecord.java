package com.example.medicalsystem.records.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Entity
@Table(name = "medical_records")
public class MedicalRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_record") private Integer idRecord;
    private String diagnosis;
    private String treatment;
    private String recommendations;
    @Column(name = "created_date") private LocalDateTime createdDate;
    @NotNull @Column(name = "id_appointment", nullable = false) private Integer appointmentId;
    public MedicalRecord() { }
    public Integer getIdRecord() { return idRecord; }
    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String value) { diagnosis = value; }
    public String getTreatment() { return treatment; }
    public void setTreatment(String value) { treatment = value; }
    public String getRecommendations() { return recommendations; }
    public void setRecommendations(String value) { recommendations = value; }
    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime value) { createdDate = value; }
    public Integer getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Integer value) { appointmentId = value; }
}
