package com.example.medicalsystem.api;

import com.example.medicalsystem.dto.RegistrationForm;
import com.example.medicalsystem.model.Appointment;
import com.example.medicalsystem.model.AppointmentType;
import com.example.medicalsystem.model.Department;
import com.example.medicalsystem.model.MedicalServiceEntity;
import com.example.medicalsystem.model.RoleEnum;
import com.example.medicalsystem.model.User;
import com.example.medicalsystem.service.AppointmentService;
import com.example.medicalsystem.service.DepartmentService;
import com.example.medicalsystem.service.UserService;
import com.example.medicalsystem.service.ServiceEntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
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
    private final ServiceEntityService serviceEntityService;

    public SystemApiService(AppointmentService appointmentService,
                               UserService userService,
                               DepartmentService departmentService,
                               ServiceEntityService serviceEntityService) {
        this.appointmentService = appointmentService;
        this.userService = userService;
        this.departmentService = departmentService;
        this.serviceEntityService = serviceEntityService;
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
        if (!principal.getAuthorities().stream().anyMatch(a -> "ADMIN".equals(a.getAuthority()))) {
            User currentUser = currentUser(principal);
            return appointmentService.findByDoctor(currentUser.getIdUser()).stream()
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
        if (!isPatientOnly(principal) && (request.status() == null || request.status().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите статус записи.");
        }
        AppointmentType type = request.type() == null ? AppointmentType.DOCTOR_VISIT : request.type();
        List<MedicalServiceEntity> selectedServices = validateAndResolve(request, type, principal);
        Appointment appointment = new Appointment();
        appointment.setType(type);
        appointment.setAppointmentDate(request.appointmentDate());
        appointment.setStatus(isPatientOnly(principal) ? "pending" : request.status());
        appointment.setComplaint(request.complaint());
        appointment.setUser(isPatientOnly(principal)
                ? currentUser(principal)
                : require(userService.findById(request.userId())));
        appointment.setServices(selectedServices);
        appointment.setDepartment(type == AppointmentType.DOCTOR_VISIT
                ? require(departmentService.findById(request.departmentId())) : null);
        appointment.setDoctor(type == AppointmentType.DOCTOR_VISIT
                ? require(userService.findById(effectiveDoctorId(request, principal))) : null);
        return toAppointmentDto(appointmentService.saveBooking(appointment));
    }

    @PutMapping("/appointments/{id}")
    @Operation(summary = "Изменить запись на приём")
    @Tag(name = "Записи на приём")
    public AppointmentDto updateAppointment(@PathVariable Integer id, @Valid @RequestBody AppointmentRequest request,
                                            @AuthenticationPrincipal UserDetails principal) {
        if (!isPatientOnly(principal) && (request.status() == null || request.status().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите статус записи.");
        }
        Appointment appointment = require(appointmentService.findById(id));
        ensureOwnerOrStaff(appointment, principal);
        AppointmentType type = request.type() == null ? appointment.getType() : request.type();
        List<MedicalServiceEntity> selectedServices = validateAndResolve(request, type, principal);
        appointment.setType(type);
        appointment.setAppointmentDate(request.appointmentDate());
        appointment.setComplaint(request.complaint());
        appointment.setDepartment(type == AppointmentType.DOCTOR_VISIT
                ? require(departmentService.findById(request.departmentId())) : null);
        appointment.setDoctor(type == AppointmentType.DOCTOR_VISIT
                ? require(userService.findById(effectiveDoctorId(request, principal))) : null);
        appointment.setServices(selectedServices);
        if (!isPatientOnly(principal)) {
            appointment.setStatus(request.status());
            appointment.setUser(require(userService.findById(request.userId())));
        }
        return toAppointmentDto(appointmentService.saveBooking(appointment));
    }

    @DeleteMapping("/appointments/{id}")
    @Operation(summary = "Удалить запись на приём")
    @Tag(name = "Записи на приём")
    public void deleteAppointment(@PathVariable Integer id, @AuthenticationPrincipal UserDetails principal) {
        ensureOwnerOrStaff(require(appointmentService.findById(id)), principal);
        appointmentService.deleteById(id);
    }

    @PostMapping("/appointments/{id}/cancel")
    @Operation(summary = "Отменить запись на приём без удаления")
    @Tag(name = "Записи на приём")
    public AppointmentDto cancelAppointment(@PathVariable Integer id,
                                             @AuthenticationPrincipal UserDetails principal) {
        Appointment appointment = require(appointmentService.findById(id));
        ensureOwnerOrStaff(appointment, principal);
        return toAppointmentDto(require(appointmentService.cancel(id)));
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
        User user = currentUser(principal);
        boolean admin = principal.getAuthorities().stream().anyMatch(a -> "ADMIN".equals(a.getAuthority()));
        boolean doctor = principal.getAuthorities().stream().anyMatch(a -> "DOCTOR".equals(a.getAuthority()));
        boolean patient = principal.getAuthorities().stream().anyMatch(a -> "PATIENT".equals(a.getAuthority()));
        boolean allowed = admin
                || (doctor && appointment.getDoctor() != null && user.getIdUser().equals(appointment.getDoctor().getIdUser()))
                || (patient && appointment.getUser() != null && user.getIdUser().equals(appointment.getUser().getIdUser()));
        if (!allowed) {
            throw new AccessDeniedException("Недостаточно прав для этой записи");
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
                value.getDepartment() == null ? null : value.getDepartment().getIdDepartment(), value.getType(),
                value.getDoctor() == null ? null : value.getDoctor().getIdUser(),
                value.getServices() == null ? List.of() : value.getServices().stream()
                        .map(service -> new ServiceDto(service.getIdService(), service.getServiceName(), service.getCost())).toList());
    }

    private Integer effectiveDoctorId(AppointmentRequest request, UserDetails principal) {
        if (principal.getAuthorities().stream().anyMatch(a -> "DOCTOR".equals(a.getAuthority()))) {
            return currentUser(principal).getIdUser();
        }
        return request.doctorId();
    }

    private List<MedicalServiceEntity> validateAndResolve(AppointmentRequest request, AppointmentType type,
                                                           UserDetails principal) {
        List<Integer> ids = request.serviceIds() == null ? List.of() : request.serviceIds().stream().distinct().toList();
        if (type == AppointmentType.DOCTOR_VISIT) {
            if (request.departmentId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Для приёма врача выберите отделение.");
            Department department = require(departmentService.findById(request.departmentId()));
            Integer doctorId = effectiveDoctorId(request, principal);
            if (doctorId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Для приёма врача выберите доктора.");
            User doctor = require(userService.findById(doctorId));
            if (doctor.getRoles() == null || !doctor.getRoles().contains(RoleEnum.DOCTOR)
                    || doctor.getDepartment() == null
                    || !department.getIdDepartment().equals(doctor.getDepartment().getIdDepartment())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Выберите врача из выбранного отделения.");
            }
            if (!ids.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Для приёма врача нельзя передавать услуги.");
            return List.of();
        }
        if (type != AppointmentType.SERVICE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестный тип записи.");
        if (request.departmentId() != null || request.doctorId() != null) throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Для записи на услуги нельзя указывать врача или отделение.");
        if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выберите хотя бы одну услугу.");
        List<MedicalServiceEntity> services = ids.stream().map(serviceEntityService::findById)
                .filter(java.util.Objects::nonNull).toList();
        if (services.size() != ids.size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Одна или несколько выбранных услуг не найдены.");
        return services;
    }

    private static PublicUserDto toPublicDto(User value) {
        return new PublicUserDto(value.getIdUser(), value.getUsername(), value.isActive(), value.getSurname(),
                value.getFirstName(), value.getMiddleName(), value.getEmail(), value.getRoles());
    }

    public record AppointmentDto(Integer id, LocalDateTime appointmentDate, String status, String complaint,
                                 Integer userId, Integer departmentId, AppointmentType type, Integer doctorId,
                                 List<ServiceDto> services) { }
    public record ServiceDto(Integer id, String name, Integer cost) { }
    public record AppointmentRequest(@NotNull @FutureOrPresent LocalDateTime appointmentDate,
                                     String status, String complaint,
                                     Integer userId, Integer departmentId, Integer doctorId,
                                     AppointmentType type, List<Integer> serviceIds) { }
    public record RoleUpdateRequest(Set<RoleEnum> roles) { }
    public record PublicUserDto(Integer id, String username, boolean active, String surname, String firstName,
                                String middleName, String email, Set<RoleEnum> roles) { }
}
