package com.example.medicalsystem.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "clinic_branches")
public class ClinicBranch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_branch")
    private Integer id;
    @NotBlank @Size(max = 120) @Column(nullable = false, unique = true, length = 120)
    private String name;
    @NotBlank @Size(max = 255) @Column(nullable = false, length = 255)
    private String address;
    @Size(max = 30) @Column(length = 30)
    private String phone;
    @ManyToMany(mappedBy = "branches")
    private List<User> doctors = new ArrayList<>();
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public List<User> getDoctors() { return doctors; }
    public void setDoctors(List<User> doctors) { this.doctors = doctors; }
}
