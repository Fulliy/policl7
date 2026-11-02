package com.example.medicalsystem.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Entity
@Table(name = "specialties")
public class Specialty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idSpecialty;

    @NotBlank(message = "Название специальности обязательно")
    @Size(min = 2, max = 50, message = "Название специальности должно быть от 2 до 50 символов")
    @Pattern(regexp = "^[\\p{L}0-9\\s\\-]+$", message = "Название может содержать буквы, цифры, пробелы и дефис")
    private String specialtyName;

    @Size(max = 255, message = "Описание должно быть не длиннее 255 символов")
    private String description;

    @OneToMany(mappedBy = "specialty")
    private List<User> users;

    @OneToMany(mappedBy = "specialty")
    private List<Role> roles;

    public Specialty() {}

    public Specialty(Integer idSpecialty, String specialtyName, String description) {
        this.idSpecialty = idSpecialty;
        this.specialtyName = specialtyName;
        this.description = description;
    }

    public Integer getIdSpecialty() { return idSpecialty; }
    public void setIdSpecialty(Integer idSpecialty) { this.idSpecialty = idSpecialty; }
    public String getSpecialtyName() { return specialtyName; }
    public void setSpecialtyName(String specialtyName) { this.specialtyName = specialtyName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<User> getUsers() { return users; }
    public void setUsers(List<User> users) { this.users = users; }
    public List<Role> getRoles() { return roles; }
    public void setRoles(List<Role> roles) { this.roles = roles; }
}
