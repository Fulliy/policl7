package com.example.medicalsystem.appointment.api;

import com.example.medicalsystem.appointment.model.MedicalServiceEntity;
import com.example.medicalsystem.appointment.repository.MedicalServiceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/services")
@Tag(name = "Медицинские услуги")
public class ServiceApiController {
    private final MedicalServiceRepository services;
    public ServiceApiController(MedicalServiceRepository services) { this.services = services; }

    @GetMapping
    @Operation(summary = "Просмотр медицинских услуг")
    public List<ServiceDto> list() { return services.findAll().stream().map(ServiceDto::from).toList(); }

    @GetMapping("/{id}")
    @Operation(summary = "Медицинская услуга по ID")
    public ServiceDto get(@PathVariable Integer id) { return ServiceDto.from(require(id)); }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','DOCTOR')")
    @Operation(summary = "Добавить медицинскую услугу")
    public ServiceDto create(@Valid @RequestBody ServiceRequest request) {
        MedicalServiceEntity value = new MedicalServiceEntity();
        value.setServiceName(request.serviceName()); value.setCost(request.cost()); value.setDescription(request.description());
        return ServiceDto.from(services.save(value));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','DOCTOR')")
    @Operation(summary = "Изменить медицинскую услугу")
    public ServiceDto update(@PathVariable Integer id, @Valid @RequestBody ServiceRequest request) {
        MedicalServiceEntity value = require(id);
        value.setServiceName(request.serviceName()); value.setCost(request.cost()); value.setDescription(request.description());
        return ServiceDto.from(services.save(value));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','DOCTOR')")
    @Operation(summary = "Удалить медицинскую услугу")
    public void delete(@PathVariable Integer id) { services.delete(require(id)); }

    private MedicalServiceEntity require(Integer id) {
        return services.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    public record ServiceRequest(@NotBlank String serviceName, Integer cost, String description) { }
    public record ServiceDto(Integer id, String serviceName, Integer cost, String description) {
        static ServiceDto from(MedicalServiceEntity value) {
            return new ServiceDto(value.getIdService(), value.getServiceName(), value.getCost(), value.getDescription());
        }
    }
}
