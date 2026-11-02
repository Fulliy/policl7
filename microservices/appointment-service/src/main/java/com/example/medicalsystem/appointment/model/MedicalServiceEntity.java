package com.example.medicalsystem.appointment.model;

import jakarta.persistence.*;

@Entity
@Table(name = "services")
public class MedicalServiceEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_service") private Integer idService;
    @Column(name = "service_name", nullable = false) private String serviceName;
    private Integer cost;
    private String description;
    public MedicalServiceEntity() { }
    public Integer getIdService() { return idService; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String value) { serviceName = value; }
    public Integer getCost() { return cost; }
    public void setCost(Integer value) { cost = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
}
