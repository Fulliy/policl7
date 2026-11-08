package com.example.medicalsystem.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Entity
@Table(name = "departments")
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idDepartment;

    @NotBlank(message = "Название отделения обязательно")
    @Size(min = 2, max = 50, message = "Название отделения должно быть от 2 до 50 символов")
    @Pattern(regexp = "^[\\p{L}0-9\\s\\-]+$", message = "Название может содержать буквы, цифры, пробелы и дефис")
    private String departmentName;

    @NotNull(message = "Этаж обязателен")
    @Min(value = 0, message = "Этаж не может быть отрицательным")
    @Max(value = 50, message = "Этаж не может превышать 50")
    private Integer floor;

    @Size(max = 10, message = "Номер кабинета должен быть не длиннее 10 символов")
    @Pattern(regexp = "^$|^[\\p{L}0-9\\-]+$", message = "Номер кабинета может содержать буквы, цифры и дефис")
    private String roomNumber;

    @OneToMany(mappedBy = "department")
    private List<User> users;

    @jakarta.persistence.ManyToMany(mappedBy = "doctorDepartments")
    private List<User> doctors;

    @OneToMany(mappedBy = "department")
    private List<Appointment> appointments;

    public Department() {}

    public Department(Integer idDepartment, String departmentName, Integer floor, String roomNumber) {
        this.idDepartment = idDepartment;
        this.departmentName = departmentName;
        this.floor = floor;
        this.roomNumber = roomNumber;
    }

    public Integer getIdDepartment() { return idDepartment; }
    public void setIdDepartment(Integer idDepartment) { this.idDepartment = idDepartment; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    public Integer getFloor() { return floor; }
    public void setFloor(Integer floor) { this.floor = floor; }
    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
    public List<User> getUsers() { return users; }
    public void setUsers(List<User> users) { this.users = users; }
    public List<User> getDoctors() { return doctors; }
    public void setDoctors(List<User> doctors) { this.doctors = doctors; }
    public List<Appointment> getAppointments() { return appointments; }
    public void setAppointments(List<Appointment> appointments) { this.appointments = appointments; }
}
