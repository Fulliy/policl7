package com.example.medicalsystem.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Entity
@Table(name = "doctor_schedule_exceptions")
public class DoctorScheduleException {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_schedule_exception") private Integer id;
    @ManyToOne(optional = false) @JoinColumn(name = "id_doctor", nullable = false)
    private User doctor;
    @NotNull @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @NotNull @Column(name = "end_date", nullable = false) private LocalDate endDate;
    @Enumerated(EnumType.STRING) @Column(name = "exception_type", nullable = false)
    private ExceptionType type;
    @Column(length = 255) private String description;
    public Integer getId() { return id; }
    public User getDoctor() { return doctor; }
    public void setDoctor(User value) { doctor = value; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate value) { startDate = value; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate value) { endDate = value; }
    public ExceptionType getType() { return type; }
    public void setType(ExceptionType value) { type = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public enum ExceptionType { DAY_OFF, HOLIDAY, VACATION, SICK_LEAVE }
}
