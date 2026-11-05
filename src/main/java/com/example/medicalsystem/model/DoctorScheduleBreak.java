package com.example.medicalsystem.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

@Entity
@Table(name = "doctor_schedule_breaks")
public class DoctorScheduleBreak {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_schedule_break") private Integer id;
    @ManyToOne(optional = false) @JoinColumn(name = "id_working_hours", nullable = false)
    private DoctorWorkingHours workingHours;
    @NotNull @Column(name = "start_time", nullable = false) private LocalTime startTime;
    @NotNull @Column(name = "end_time", nullable = false) private LocalTime endTime;
    public Integer getId() { return id; }
    public DoctorWorkingHours getWorkingHours() { return workingHours; }
    public void setWorkingHours(DoctorWorkingHours value) { workingHours = value; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime value) { startTime = value; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime value) { endTime = value; }
}
