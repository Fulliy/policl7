package com.example.medicalsystem.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Entity
@Table(name = "patient_affiliations", uniqueConstraints = @UniqueConstraint(
        name = "uq_patient_affiliation_demo_key",
        columnNames = {"id_patient", "medical_center_name", "doctor_name", "attached_at"}))
public class PatientAffiliation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_affiliation")
    private Integer idAffiliation;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_patient", nullable = false)
    private User patient;

    @NotBlank
    @Size(max = 200)
    @Column(name = "medical_center_name", nullable = false, length = 200)
    private String medicalCenterName;

    @NotBlank
    @Size(max = 150)
    @Column(name = "doctor_name", nullable = false, length = 150)
    private String doctorName;

    @NotNull
    @Column(name = "attached_at", nullable = false)
    private LocalDate attachedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AffiliationStatus status = AffiliationStatus.ACTIVE;

    public Integer getIdAffiliation() { return idAffiliation; }
    public void setIdAffiliation(Integer idAffiliation) { this.idAffiliation = idAffiliation; }
    public User getPatient() { return patient; }
    public void setPatient(User patient) { this.patient = patient; }
    public String getMedicalCenterName() { return medicalCenterName; }
    public void setMedicalCenterName(String medicalCenterName) { this.medicalCenterName = medicalCenterName; }
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    public LocalDate getAttachedAt() { return attachedAt; }
    public void setAttachedAt(LocalDate attachedAt) { this.attachedAt = attachedAt; }
    public AffiliationStatus getStatus() { return status; }
    public void setStatus(AffiliationStatus status) { this.status = status; }

    public enum AffiliationStatus { ACTIVE, ENDED }
}
