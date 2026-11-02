package com.example.medicalsystem.api;

import com.example.medicalsystem.dto.RegistrationForm;
import com.example.medicalsystem.model.Appointment;
import com.example.medicalsystem.model.Department;
import com.example.medicalsystem.model.RoleEnum;
import com.example.medicalsystem.model.User;
import com.example.medicalsystem.service.AppointmentService;
import com.example.medicalsystem.service.DepartmentService;
import com.example.medicalsystem.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.EnumSet;

@Service
public class SystemApiService {

    private final AppointmentService appointmentService;
    private final UserService userService;
    private final DepartmentService departmentService;

    public SystemApiService(AppointmentService appointmentService,
                               UserService userService,
                               DepartmentService departmentService) {
        this.appointmentService = appointmentService;
        this.userService = userService;
        this.departmentService = departmentService;
    }

    @GetMapping("/home")
    @Operation(summary = "Сведения о сервисе")
    @Tag(name = "Главная")
    public Map<String, String> home() {
        return Map.of("name", "Медицинская информационная система", "status", "Работает");
    }

    @PostMapping("/auth/register")
    @Operation(summary = "Зарегистрировать пациента")
    @Tag(name = "Регистрация")
    public PublicUserDto register(@Valid @RequestBody RegistrationForm form) {
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пароли не совпадают");
        }
        try {
            return toPublicDto(userService.register(form));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
    }

    @GetMapping("/admin/users")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Получить пользователей для администрирования")
    @Tag(name = "Администрирование")
    public List<PublicUserDto> adminUsers() {
        return userService.findAll().stream().map(SystemApiService::toPublicDto).toList();
    }

    @GetMapping("/admin/users/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Получить пользователя для администрирования")
    @Tag(name = "Администрирование")
    public PublicUserDto adminUser(@PathVariable Integer id) {
        return toPublicDto(require(userService.findById(id)));
    }

    @PutMapping("/admin/users/{id}/roles")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Изменить роли пользователя")
    @Tag(name = "Администрирование")
    public PublicUserDto updateRoles(@PathVariable Integer id, @RequestBody RoleUpdateRequest request) {
        require(userService.findById(id));
        userService.updateRoles(id, request.roles() == null ? Set.of() : request.roles());
        return toPublicDto(require(userService.findById(id)));
    }

    @GetMapping("/appointments")
    @Operation(summary = "Получить записи на приём")
    @Tag(name = "Записи на приём")
    public List<AppointmentDto> appointments(@AuthenticationPrincipal UserDetails principal) {
        if (isPatientOnly(principal)) {
            User currentUser = currentUser(principal);
            return appointmentService.findByUser(currentUser.getIdUser()).stream()
                    .map(SystemApiService::toAppointmentDto).toList();
        }
        return appointmentService.findAll().stream().map(SystemApiService::toAppointmentDto).toList();
    }

    @GetMapping("/appointments/{id}")
    @Operation(summary = "Получить запись на приём по ID")
    @Tag(name = "Записи на приём")
    public AppointmentDto appointment(@PathVariable Integer id, @AuthenticationPrincipal UserDetails principal) {
        Appointment appointment = require(appointmentService.findById(id));
        ensureOwnerOrStaff(appointment, principal);
        return toAppointmentDto(appointment);
    }

    @PostMapping("/appointments")
    @Operation(summary = "Создать запись на приём")
    @Tag(name = "Записи на приём")
    public AppointmentDto createAppointment(@Valid @RequestBody AppointmentRequest request,
                                            @AuthenticationPrincipal UserDetails principal) {
        Appointment appointment = new Appointment();
        appointment.setAppointmentDate(request.appointmentDate());
        appointment.setStatus(isPatientOnly(principal) ? "Запланирован" : request.status());
        appointment.setComplaint(request.complaint());
        appointment.setUser(isPatientOnly(principal)
                ? currentUser(principal)
                : require(userService.findById(request.userId())));
        appointment.setDepartment(require(departmentService.findById(request.departmentId())));
        return toAppointmentDto(appointmentService.save(appointment));
    }

    @PutMapping("/appointments/{id}")
    @Operation(summary = "Изменить запись на приём")
    @Tag(name = "Записи на приём")
    public AppointmentDto updateAppointment(@PathVariable Integer id, @Valid @RequestBody AppointmentRequest request,
                                            @AuthenticationPrincipal UserDetails principal) {
        Appointment appointment = require(appointmentService.findById(id));
        ensureOwnerOrStaff(appointment, principal);
        appointment.setAppointmentDate(request.appointmentDate());
        appointment.setComplaint(request.complaint());
        appointment.setDepartment(require(departmentService.findById(request.departmentId())));
        if (!isPatientOnly(principal)) {
            appointment.setStatus(request.status());
            appointment.setUser(require(userService.findById(request.userId())));
        }
        return toAppointmentDto(appointmentService.save(appointment));
    }

    @DeleteMapping("/appointments/{id}")
    @Operation(summary = "Удалить запись на приём")
    @Tag(name = "Записи на приём")
    public void deleteAppointment(@PathVariable Integer id, @AuthenticationPrincipal UserDetails principal) {
        ensureOwnerOrStaff(require(appointmentService.findById(id)), principal);
        appointmentService.deleteById(id);
    }

    private User currentUser(UserDetails principal) {
        return require(userService.findByUsername(principal.getUsername()));
    }

    private static boolean isPatientOnly(UserDetails principal) {
        boolean patient = principal.getAuthorities().stream().anyMatch(a -> "PATIENT".equals(a.getAuthority()));
        boolean staff = principal.getAuthorities().stream()
                .anyMatch(a -> "ADMIN".equals(a.getAuthority()) || "DOCTOR".equals(a.getAuthority()));
        return patient && !staff;
    }

    private void ensureOwnerOrStaff(Appointment appointment, UserDetails principal) {
        if (isPatientOnly(principal) && (appointment.getUser() == null
                || !currentUser(principal).getIdUser().equals(appointment.getUser().getIdUser()))) {
            throw new AccessDeniedException("Можно работать только со своими записями");
        }
    }

    private static <T> T require(T value) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Запись не найдена");
        }
        return value;
    }

    private static AppointmentDto toAppointmentDto(Appointment value) {
        return new AppointmentDto(value.getIdAppointment(), value.getAppointmentDate(), value.getStatus(), value.getComplaint(),
                value.getUser() == null ? null : value.getUser().getIdUser(),
                value.getDepartment() == null ? null : value.getDepartment().getIdDepartment());
    }

    private static PublicUserDto toPublicDto(User value) {
        return new PublicUserDto(value.getIdUser(), value.getUsername(), value.isActive(), value.getSurname(),
                value.getFirstName(), value.getMiddleName(), value.getEmail(), value.getRoles());
    }

    public record AppointmentDto(Integer id, LocalDateTime appointmentDate, String status, String complaint,
                                 Integer userId, Integer departmentId) { }
    public record AppointmentRequest(@NotNull @FutureOrPresent LocalDateTime appointmentDate,
                                     @NotBlank String status, String complaint,
                                     Integer userId, @NotNull Integer departmentId) { }
    public record RoleUpdateRequest(Set<RoleEnum> roles) { }
    public record PublicUserDto(Integer id, String username, boolean active, String surname, String firstName,
                                String middleName, String email, Set<RoleEnum> roles) { }
}
