package com.example.medicalsystem.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity
@Table(name = "doctor_working_hours")
public class DoctorWorkingHours {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_working_hours") private Integer id;
    @ManyToOne(optional = false) @JoinColumn(name = "id_doctor", nullable = false)
    private User doctor;
    @NotNull @Min(1) @Max(7) @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;
    @Enumerated(EnumType.STRING) @Column(name = "week_parity")
    private WeekParity weekParity;
    @NotNull @Column(name = "start_time", nullable = false)
    private LocalTime startTime;
    @NotNull @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public User getDoctor() { return doctor; }
    public void setDoctor(User doctor) { this.doctor = doctor; }
    public Integer getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(Integer dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public WeekParity getWeekParity() { return weekParity; }
    public void setWeekParity(WeekParity weekParity) { this.weekParity = weekParity; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public enum WeekParity { EVEN, ODD }
}
