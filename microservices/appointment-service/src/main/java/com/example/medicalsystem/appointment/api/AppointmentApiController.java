package com.example.medicalsystem.appointment.api;

import com.example.medicalsystem.appointment.model.Appointment;
import com.example.medicalsystem.appointment.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/appointments")
@Tag(name = "Записи на приём")
public class AppointmentApiController {
    private final AppointmentService service;
    public AppointmentApiController(AppointmentService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Список приёмов в пределах прав пользователя")
    public List<AppointmentDto> list(Authentication auth) {
        return service.findVisible(auth).stream().map(this::toDto).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить приём с проверкой владельца")
    public AppointmentDto get(@PathVariable Integer id, Authentication auth) {
        return toDto(service.getVisible(id, auth));
    }

    @PostMapping
    @Operation(summary = "Создать запись на приём")
    @PreAuthorize("hasAnyAuthority('PATIENT','DOCTOR','ADMIN')")
    public AppointmentDto create(@Valid @RequestBody Request body, Authentication auth) {
        return toDto(service.create(body.toRequest(), auth));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Изменить запись с проверкой владельца")
    public AppointmentDto update(@PathVariable Integer id, @Valid @RequestBody Request body, Authentication auth) {
        return toDto(service.update(id, body.toRequest(), auth));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить запись с проверкой владельца")
    public void delete(@PathVariable Integer id, Authentication auth) { service.delete(id, auth); }

    public record Request(@NotNull @FutureOrPresent LocalDateTime appointmentDate, String status,
                          String complaint, Integer userId, Integer departmentId, Integer doctorId,
                          com.example.medicalsystem.appointment.model.AppointmentType type, List<Integer> serviceIds) {
        AppointmentService.AppointmentRequest toRequest() {
            return new AppointmentService.AppointmentRequest(appointmentDate, status, complaint,
                    userId, departmentId, doctorId, type, serviceIds);
        }
    }

    public record AppointmentDto(Integer id, LocalDateTime appointmentDate, String status, String complaint,
                                 Integer userId, Integer doctorId, Integer departmentId,
                                 com.example.medicalsystem.appointment.model.AppointmentType type,
                                 List<AppointmentService.ServiceSummary> services) {
    }

    private AppointmentDto toDto(Appointment value) {
        return new AppointmentDto(value.getIdAppointment(), value.getAppointmentDate(), value.getStatus(),
                value.getComplaint(), value.getUserId(), value.getDoctorId(), value.getDepartmentId(),
                value.getType(), service.servicesFor(value.getIdAppointment()));
    }
}
