package com.example.medicalsystem.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Entity
@Table(name = "patient_referrals", uniqueConstraints = @UniqueConstraint(
        name = "uq_patient_referral_demo_key",
        columnNames = {"id_patient", "referral_date", "target", "issued_by_name"}))
public class PatientReferral {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_referral")
    private Integer idReferral;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_patient", nullable = false)
    private User patient;

    @NotNull
    @Column(name = "referral_date", nullable = false)
    private LocalDate referralDate;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String target;

    @NotBlank
    @Size(max = 150)
    @Column(name = "issued_by_name", nullable = false, length = 150)
    private String issuedByName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReferralStatus status = ReferralStatus.ACTIVE;

    public Integer getIdReferral() { return idReferral; }
    public void setIdReferral(Integer idReferral) { this.idReferral = idReferral; }
    public User getPatient() { return patient; }
    public void setPatient(User patient) { this.patient = patient; }
    public LocalDate getReferralDate() { return referralDate; }
    public void setReferralDate(LocalDate referralDate) { this.referralDate = referralDate; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getIssuedByName() { return issuedByName; }
    public void setIssuedByName(String issuedByName) { this.issuedByName = issuedByName; }
    public ReferralStatus getStatus() { return status; }
    public void setStatus(ReferralStatus status) { this.status = status; }

    public enum ReferralStatus { ACTIVE, COMPLETED, CANCELLED }
}
