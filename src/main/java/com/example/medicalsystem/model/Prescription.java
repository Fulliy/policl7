package com.example.medicalsystem.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "prescriptions")
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idPrescription;

    @NotBlank(message = "Название лекарства обязательно")
    @Size(min = 2, max = 100, message = "Название лекарства должно быть от 2 до 100 символов")
    private String medicationName;

    @Size(max = 50, message = "Дозировка должна быть не длиннее 50 символов")
    @Pattern(regexp = "^$|^[0-9\\p{L}\\s\\-]+$", message = "Дозировка может содержать буквы, цифры, пробелы и дефис")
    private String dosage;

    @Size(max = 50, message = "Частота должна быть не длиннее 50 символов")
    private String frequency;

    @NotNull(message = "Длительность курса обязательна")
    @Min(value = 1, message = "Длительность должна быть не менее 1 дня")
    @Max(value = 365, message = "Длительность не может превышать 365 дней")
    private Integer durationDays;

    @NotNull(message = "Медицинская запись обязательна")
    @ManyToOne
    @JoinColumn(name = "id_record")
    private MedicalRecord medicalRecord;

    @NotNull(message = "Услуга обязательна")
    @ManyToOne
    @JoinColumn(name = "id_service")
    private MedicalServiceEntity service;

    public Prescription() {
    }

    public Prescription(Integer idPrescription, String medicationName, String dosage,
                        String frequency, Integer durationDays, MedicalRecord medicalRecord,
                        MedicalServiceEntity service) {
        this.idPrescription = idPrescription;
        this.medicationName = medicationName;
        this.dosage = dosage;
        this.frequency = frequency;
        this.durationDays = durationDays;
        this.medicalRecord = medicalRecord;
        this.service = service;
    }

    public Integer getIdPrescription() { return idPrescription; }
    public void setIdPrescription(Integer idPrescription) { this.idPrescription = idPrescription; }
    public String getMedicationName() { return medicationName; }
    public void setMedicationName(String medicationName) { this.medicationName = medicationName; }
    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }
    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public MedicalRecord getMedicalRecord() { return medicalRecord; }
    public void setMedicalRecord(MedicalRecord medicalRecord) { this.medicalRecord = medicalRecord; }
    public MedicalServiceEntity getService() { return service; }
    public void setService(MedicalServiceEntity service) { this.service = service; }
}
