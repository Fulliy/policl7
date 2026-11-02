package com.example.medicalsystem.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idRole;

    @NotBlank(message = "Название роли обязательно")
    @Size(min = 2, max = 40, message = "Название роли должно быть от 2 до 40 символов")
    @Pattern(regexp = "^[\\p{L}0-9\\s\\-]+$", message = "Название роли может содержать буквы, цифры, пробелы и дефис")
    private String roleName;

    @Size(max = 255, message = "Описание должно быть не длиннее 255 символов")
    private String description;

    @ManyToOne
    @JoinColumn(name = "id_specialty")
    private Specialty specialty;

    public Role() {
    }

    public Role(Integer idRole, String roleName, String description, Specialty specialty) {
        this.idRole = idRole;
        this.roleName = roleName;
        this.description = description;
        this.specialty = specialty;
    }

    public Integer getIdRole() { return idRole; }
    public void setIdRole(Integer idRole) { this.idRole = idRole; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Specialty getSpecialty() { return specialty; }
    public void setSpecialty(Specialty specialty) { this.specialty = specialty; }
}
