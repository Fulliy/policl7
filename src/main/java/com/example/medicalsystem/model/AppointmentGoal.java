package com.example.medicalsystem.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "appointment_goals")
public class AppointmentGoal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_goal")
    private Integer id;
    @NotBlank @Size(max = 120) @Column(nullable = false, length = 120)
    private String name;
    @NotBlank @Size(max = 40) @Column(name = "icon_key", nullable = false, length = 40)
    private String iconKey;
    @NotNull @Enumerated(EnumType.STRING) @Column(name = "goal_group", nullable = false, length = 30)
    private GoalGroup goalGroup = GoalGroup.POLYCLINIC;
    @NotNull @Enumerated(EnumType.STRING) @Column(name = "scenario_type", nullable = false, length = 30)
    private ScenarioType scenarioType = ScenarioType.SPECIALTY;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(nullable = false) private boolean paid;
    @Column(nullable = false) private boolean active = true;
    @Size(max = 500) @Column(length = 500) private String description;
    @Size(max = 500) @Column(name = "external_url", length = 500) private String externalUrl;
    @ManyToMany
    @JoinTable(name = "appointment_goal_specialties",
            joinColumns = @JoinColumn(name = "id_goal"), inverseJoinColumns = @JoinColumn(name = "id_specialty"))
    private List<Specialty> specialties = new ArrayList<>();

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIconKey() { return iconKey; }
    public void setIconKey(String iconKey) { this.iconKey = iconKey; }
    public GoalGroup getGoalGroup() { return goalGroup; }
    public void setGoalGroup(GoalGroup goalGroup) { this.goalGroup = goalGroup; }
    public ScenarioType getScenarioType() { return scenarioType; }
    public void setScenarioType(ScenarioType scenarioType) { this.scenarioType = scenarioType; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getExternalUrl() { return externalUrl; }
    public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl; }
    public List<Specialty> getSpecialties() { return specialties; }
    public void setSpecialties(List<Specialty> specialties) { this.specialties = specialties; }

    public enum GoalGroup { POLYCLINIC, OTHER_INSTITUTIONS }
    public enum ScenarioType { ORVI_DUTY, SPECIALTY, DOCTORS, PAID_SERVICE, EXTERNAL }
}
