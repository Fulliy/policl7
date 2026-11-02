package com.example.medicalsystem.records.model;

import jakarta.persistence.*;

@Entity
@Table(name = "prescriptions")
public class Prescription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_prescription") private Integer idPrescription;
    @Column(name = "medication_name") private String medicationName;
    private String dosage;
    private String frequency;
    @Column(name = "duration_days") private Integer durationDays;
    @Column(name = "id_record", nullable = false) private Integer medicalRecordId;
    @Column(name = "id_service") private Integer serviceId;
    public Prescription() { }
    public Integer getIdPrescription() { return idPrescription; }
    public String getMedicationName() { return medicationName; }
    public void setMedicationName(String value) { medicationName = value; }
    public String getDosage() { return dosage; }
    public void setDosage(String value) { dosage = value; }
    public String getFrequency() { return frequency; }
    public void setFrequency(String value) { frequency = value; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer value) { durationDays = value; }
    public Integer getMedicalRecordId() { return medicalRecordId; }
    public void setMedicalRecordId(Integer value) { medicalRecordId = value; }
    public Integer getServiceId() { return serviceId; }
    public void setServiceId(Integer value) { serviceId = value; }
}
