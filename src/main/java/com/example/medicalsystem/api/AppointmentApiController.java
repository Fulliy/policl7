package com.example.medicalsystem.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/appointments")
@Tag(name = "Записи на приём")
public class AppointmentApiController {
    private final SystemApiService api;
    public AppointmentApiController(SystemApiService api) { this.api = api; }

    @GetMapping @Operation(summary = "Список записей (пациенту — только свои)")
    public java.util.List<SystemApiService.AppointmentDto> list(@AuthenticationPrincipal UserDetails user) { return api.appointments(user); }
    @GetMapping("/{id}") @Operation(summary = "Запись по ID")
    public SystemApiService.AppointmentDto get(@PathVariable Integer id, @AuthenticationPrincipal UserDetails user) { return api.appointment(id, user); }
    @PostMapping @Operation(summary = "Создать запись на приём")
    public SystemApiService.AppointmentDto create(@Valid @RequestBody SystemApiService.AppointmentRequest body, @AuthenticationPrincipal UserDetails user) {
        return api.createAppointment(body, user);
    }
    @PutMapping("/{id}") @Operation(summary = "Изменить запись")
    public SystemApiService.AppointmentDto update(@PathVariable Integer id, @Valid @RequestBody SystemApiService.AppointmentRequest body, @AuthenticationPrincipal UserDetails user) {
        return api.updateAppointment(id, body, user);
    }
    @DeleteMapping("/{id}") @Operation(summary = "Удалить запись")
    public void delete(@PathVariable Integer id, @AuthenticationPrincipal UserDetails user) { api.deleteAppointment(id, user); }
    @PostMapping("/{id}/cancel") @Operation(summary = "Отменить запись без удаления")
    public SystemApiService.AppointmentDto cancel(@PathVariable Integer id, @AuthenticationPrincipal UserDetails user) {
        return api.cancelAppointment(id, user);
    }
}
