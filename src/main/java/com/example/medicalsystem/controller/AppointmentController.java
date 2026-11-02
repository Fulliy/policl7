package com.example.medicalsystem.controller;

import com.example.medicalsystem.model.Appointment;
import com.example.medicalsystem.service.AppointmentService;
import com.example.medicalsystem.service.UserService;
import com.example.medicalsystem.service.DepartmentService;
import com.example.medicalsystem.service.ServiceEntityService;
import com.example.medicalsystem.model.MedicalServiceEntity;
import com.example.medicalsystem.model.RoleEnum;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Controller
@RequestMapping("/appointments")
@Tag(name = "Appointments", description = "Управление записями на приём")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final UserService userService;
    private final DepartmentService departmentService;
    private final ServiceEntityService serviceEntityService;
    private final Validator validator;

    public AppointmentController(AppointmentService appointmentService,
                                 UserService userService,
                                 DepartmentService departmentService,
                                 ServiceEntityService serviceEntityService,
                                 Validator validator) {
        this.appointmentService = appointmentService;
        this.userService = userService;
        this.departmentService = departmentService;
        this.serviceEntityService = serviceEntityService;
        this.validator = validator;
    }

    @GetMapping
    @Operation(summary = "Список записей на приём")
    public String list(@RequestParam(defaultValue = "1") int page,
                       @RequestParam(required = false) String searchField,
                       @RequestParam(required = false) String searchValue,
                       @RequestParam(defaultValue = "upcoming") String visitPeriod,
                       @RequestParam(required = false) String status,
                       Model model,
                       @AuthenticationPrincipal UserDetails principal) {
        com.example.medicalsystem.model.User currentUser = userService.findByUsername(principal.getUsername());
        boolean patientOnly = isPatientOnly(principal);
        boolean admin = principal.getAuthorities().stream().anyMatch(a -> "ADMIN".equals(a.getAuthority()));
        if (!patientOnly && !admin) return "redirect:/";
        Page<Appointment> appointments = admin
                ? appointmentService.searchAdmin(searchField, searchValue, status, page)
                : appointmentService.searchForUser(currentUser.getIdUser(), searchField, searchValue, page, visitPeriod);

        model.addAttribute("appointments", appointments.getContent());
        model.addAttribute("isPatientOnly", patientOnly);
        if (patientOnly) {
            model.addAttribute("patient", currentUser);
        }
        addPagination(model, appointments);
        model.addAttribute("searchField", searchField);
        model.addAttribute("searchValue", searchValue);
        model.addAttribute("visitPeriod", visitPeriod);
        model.addAttribute("statusFilter", status);
        if (admin) {
            java.time.LocalDateTime today = java.time.LocalDate.now().atStartOfDay();
            model.addAttribute("appointmentsToday", appointmentService.findByDateRange(today, today.plusDays(1)).stream()
                    .filter(appointment -> appointment.getAppointmentDate().isBefore(today.plusDays(1))).count());
        }

        return admin ? "admin_appointments" : "appointments";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model, @AuthenticationPrincipal UserDetails principal) {
        Appointment appointment = new Appointment();
        if (isPatientOnly(principal)) {
            appointment.setStatus("Запланирован");
        }
        model.addAttribute("appointment", appointment);
        com.example.medicalsystem.model.User currentUser = userService.findByUsername(principal.getUsername());
        model.addAttribute("users", isPatientOnly(principal) ? java.util.List.of(currentUser) : userService.findAll());
        model.addAttribute("doctors", findDoctors());
        model.addAttribute("departments", departmentService.findAll());
        model.addAttribute("services", serviceEntityService.findAll());
        model.addAttribute("isPatientOnly", isPatientOnly(principal));
        return "appointmentForm";
    }

    @PostMapping("/save")
    @Operation(summary = "Создание записи на приём")
    public String save(@ModelAttribute("appointment") Appointment appointment,
                       BindingResult result,
                       @RequestParam(name = "serviceIds", required = false) java.util.List<Integer> serviceIds,
                       @RequestParam(name = "doctorId", required = false) Integer doctorId,
                       Model model,
                       @AuthenticationPrincipal UserDetails principal) {
        if (isPatientOnly(principal)) {
            appointment.setUser(userService.findByUsername(principal.getUsername()));
            appointment.setStatus("Запланирован");
        }
        appointment.setServices(findServices(serviceIds));
        assignDoctor(appointment, doctorId, result);
        validateAppointment(appointment, result);
        validateDoctorAvailability(appointment, null, result);
        if (result.hasErrors()) {
            model.addAttribute("appointment", appointment);
            com.example.medicalsystem.model.User currentUser = userService.findByUsername(principal.getUsername());
            model.addAttribute("users", isPatientOnly(principal) ? java.util.List.of(currentUser) : userService.findAll());
            model.addAttribute("doctors", findDoctors());
            model.addAttribute("departments", departmentService.findAll());
            model.addAttribute("services", serviceEntityService.findAll());
            model.addAttribute("isPatientOnly", isPatientOnly(principal));
            return "appointmentForm";
        }
        appointmentService.save(appointment);
        return "redirect:/appointments";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Integer id, Model model,
                               @AuthenticationPrincipal UserDetails principal) {
        Appointment appointment = appointmentService.findById(id);
        ensureOwnerOrStaff(appointment, principal);
        model.addAttribute("appointment", appointment);
        com.example.medicalsystem.model.User currentUser = userService.findByUsername(principal.getUsername());
        model.addAttribute("users", isPatientOnly(principal) ? java.util.List.of(currentUser) : userService.findAll());
        model.addAttribute("doctors", findDoctors());
        model.addAttribute("departments", departmentService.findAll());
        model.addAttribute("services", serviceEntityService.findAll());
        model.addAttribute("isPatientOnly", isPatientOnly(principal));
        return "appointmentForm";
    }

    @PostMapping("/update")
    public String update(@RequestParam Integer id,
                         @RequestParam(name = "serviceIds", required = false) java.util.List<Integer> serviceIds,
                         @RequestParam(name = "doctorId", required = false) Integer doctorId,
                         @ModelAttribute("appointment") Appointment appointment,
                         BindingResult result,
                         Model model,
                         @AuthenticationPrincipal UserDetails principal) {
        Appointment existing = appointmentService.findById(id);
        ensureOwnerOrStaff(existing, principal);
        if (isPatientOnly(principal)) {
            appointment.setUser(existing.getUser());
            appointment.setStatus(existing.getStatus());
        }
        appointment.setServices(findServices(serviceIds));
        assignDoctor(appointment, doctorId, result);
        validateAppointment(appointment, result);
        validateDoctorAvailability(appointment, id, result);
        if (result.hasErrors()) {
            appointment.setIdAppointment(id);
            model.addAttribute("appointment", appointment);
            com.example.medicalsystem.model.User currentUser = userService.findByUsername(principal.getUsername());
            model.addAttribute("users", isPatientOnly(principal) ? java.util.List.of(currentUser) : userService.findAll());
            model.addAttribute("doctors", findDoctors());
            model.addAttribute("departments", departmentService.findAll());
            model.addAttribute("services", serviceEntityService.findAll());
            model.addAttribute("isPatientOnly", isPatientOnly(principal));
            return "appointmentForm";
        }
        appointment.setIdAppointment(id);
        appointmentService.save(appointment);
        return "redirect:/appointments";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Integer id, @AuthenticationPrincipal UserDetails principal) {
        ensureOwnerOrStaff(appointmentService.findById(id), principal);
        appointmentService.deleteById(id);
        return "redirect:/appointments";
    }

    private boolean isPatientOnly(UserDetails principal) {
        boolean patient = principal.getAuthorities().stream().anyMatch(a -> "PATIENT".equals(a.getAuthority()));
        boolean staff = principal.getAuthorities().stream()
                .anyMatch(a -> "ADMIN".equals(a.getAuthority()) || "DOCTOR".equals(a.getAuthority()));
        return patient && !staff;
    }

    private void ensureOwnerOrStaff(Appointment appointment, UserDetails principal) {
        if (appointment == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Запись не найдена");
        }
        if (isPatientOnly(principal)) {
            com.example.medicalsystem.model.User currentUser = userService.findByUsername(principal.getUsername());
            if (appointment.getUser() == null || !currentUser.getIdUser().equals(appointment.getUser().getIdUser())) {
                throw new AccessDeniedException("Можно работать только со своими записями");
            }
        }
    }

    private void validateAppointment(Appointment appointment, BindingResult result) {
        validator.validate(appointment).forEach(error -> result.rejectValue(
                error.getPropertyPath().toString(), "appointment.validation", error.getMessage()));
    }

    private java.util.List<MedicalServiceEntity> findServices(java.util.List<Integer> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) return java.util.List.of();
        return serviceIds.stream().distinct().map(serviceEntityService::findById)
                .filter(java.util.Objects::nonNull).toList();
    }

    private java.util.List<com.example.medicalsystem.model.User> findDoctors() {
        return userService.findAll().stream()
                .filter(user -> user.getRoles() != null && user.getRoles().contains(RoleEnum.DOCTOR))
                .toList();
    }

    private void validateDoctorAvailability(Appointment appointment, Integer excludeId, BindingResult result) {
        if (appointment.getDoctor() != null && appointment.getAppointmentDate() != null
                && !appointmentService.isDoctorAvailable(appointment.getDoctor().getIdUser(),
                appointment.getAppointmentDate(), excludeId)) {
            result.rejectValue("doctor", "appointment.doctor.busy", "В это время доктор уже занят. Выберите другое время или врача.");
        }
    }

    private void assignDoctor(Appointment appointment, Integer doctorId, BindingResult result) {
        com.example.medicalsystem.model.User doctor = doctorId == null ? null : userService.findById(doctorId);
        if (doctor == null || doctor.getRoles() == null || !doctor.getRoles().contains(RoleEnum.DOCTOR)) {
            result.rejectValue("doctor", "appointment.doctor.required", "Выберите доктора.");
            return;
        }
        appointment.setDoctor(doctor);
    }

    private void addPagination(Model model, Page<?> page) {
        model.addAttribute("currentPage", page.getNumber() + 1);
        model.addAttribute("totalPages", Math.max(page.getTotalPages(), 1));
        model.addAttribute("hasNext", page.hasNext());
        model.addAttribute("hasPrevious", page.hasPrevious());
    }
}
