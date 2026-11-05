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
import jakarta.validation.constraints.Size;

import java.util.List;

@Entity
@Table(name = "services")
public class MedicalServiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idService;

    @NotBlank(message = "Название услуги обязательно")
    @Size(min = 2, max = 100, message = "Название услуги должно быть от 2 до 100 символов")
    private String serviceName;

    @NotNull(message = "Стоимость обязательна")
    @Min(value = 0, message = "Стоимость не может быть отрицательной")
    @Max(value = 1000000, message = "Стоимость не может превышать 1 000 000")
    private Integer cost;

    @NotNull
    @Min(value = 5, message = "Длительность услуги должна быть не менее 5 минут")
    @Max(value = 480, message = "Длительность услуги не может превышать 8 часов")
    @jakarta.persistence.Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes = 30;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @jakarta.persistence.Column(name = "service_type", nullable = false, length = 20)
    private ServiceType serviceType = ServiceType.PRIMARY;

    @Size(max = 500, message = "Описание должно быть не длиннее 500 символов")
    private String description;

    @OneToMany(mappedBy = "service")
    private List<Prescription> prescriptions;

    public MedicalServiceEntity() {}

    public MedicalServiceEntity(Integer idService, String serviceName, Integer cost, String description) {
        this.idService = idService;
        this.serviceName = serviceName;
        this.cost = cost;
        this.description = description;
    }

    public Integer getIdService() { return idService; }
    public void setIdService(Integer idService) { this.idService = idService; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public Integer getCost() { return cost; }
    public void setCost(Integer cost) { this.cost = cost; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public ServiceType getServiceType() { return serviceType; }
    public void setServiceType(ServiceType serviceType) { this.serviceType = serviceType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<Prescription> getPrescriptions() { return prescriptions; }
    public void setPrescriptions(List<Prescription> prescriptions) { this.prescriptions = prescriptions; }

    public enum ServiceType { PRIMARY, REPEAT, PROCEDURE }
}
