package com.example.medicalsystem.controller;

import com.example.medicalsystem.model.Appointment;
import com.example.medicalsystem.model.AppointmentType;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    public String showCreateForm(@RequestParam(required = false) Integer doctorId,
                                 @RequestParam(required = false) Integer specialtyId,
                                 Model model, @AuthenticationPrincipal UserDetails principal) {
        Appointment appointment = new Appointment();
        appointment.setType(AppointmentType.DOCTOR_VISIT);
        com.example.medicalsystem.model.User selectedDoctor = doctorId == null ? null : userService.findById(doctorId);
        if (selectedDoctor != null && selectedDoctor.getRoles() != null
                && selectedDoctor.getRoles().contains(RoleEnum.DOCTOR)) {
            appointment.setDoctor(selectedDoctor);
        }
        if (isPatientOnly(principal)) {
            appointment.setStatus("pending");
        }
        model.addAttribute("appointment", appointment);
        com.example.medicalsystem.model.User currentUser = userService.findByUsername(principal.getUsername());
        model.addAttribute("users", isPatientOnly(principal) ? java.util.List.of(currentUser) : userService.findAll());
        model.addAttribute("doctors", findDoctors(specialtyId));
        model.addAttribute("departments", departmentService.findAll());
        model.addAttribute("services", serviceEntityService.findAll());
        model.addAttribute("isPatientOnly", isPatientOnly(principal));
        model.addAttribute("selectedSpecialtyId", specialtyId);
        return "appointmentForm";
    }

    @PostMapping("/save")
    @Operation(summary = "Создание записи на приём")
    public Object save(@ModelAttribute("appointment") Appointment appointment,
                       BindingResult result,
                       @RequestParam(name = "serviceIds", required = false) java.util.List<Integer> serviceIds,
                       @RequestParam(name = "doctorId", required = false) Integer doctorId,
                       @RequestParam(name = "holdId", required = false) Integer holdId,
                       Model model,
                       @AuthenticationPrincipal UserDetails principal,
                       @RequestHeader(value = "X-Requested-With", required = false) String requestedWith) {
        boolean ajax = "XMLHttpRequest".equals(requestedWith);
        boolean administrator = hasAuthority(principal, "ADMIN");
        boolean newAppointment = appointment.getIdAppointment() == null;
        if (isPatientOnly(principal)) {
            appointment.setUser(userService.findByUsername(principal.getUsername()));
            appointment.setStatus("pending");
        }
        applyAppointmentType(appointment, serviceIds, doctorId, result);
        validateAppointment(appointment, result);
        validateDoctorAvailability(appointment, null, result);
        if (result.hasErrors()) {
            if (ajax) return org.springframework.http.ResponseEntity.badRequest().body(
                    java.util.Map.of("message", result.getAllErrors().get(0).getDefaultMessage()));
            model.addAttribute("appointment", appointment);
            addFormOptions(model, principal);
            return "appointmentForm";
        }
        appointmentService.saveBooking(appointment, holdId, administrator, administrator && newAppointment);
        if (ajax) return org.springframework.http.ResponseEntity.ok(java.util.Map.of(
                "message", "Запись создана.", "redirectUrl", "/appointments"));
        return "redirect:/appointments";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Integer id, Model model,
                               @AuthenticationPrincipal UserDetails principal) {
        Appointment appointment = appointmentService.findById(id);
        ensureOwnerOrStaff(appointment, principal);
        model.addAttribute("appointment", appointment);
        addFormOptions(model, principal);
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
            if (existing.isRequiresReschedule()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Приём требует переноса, обратитесь в регистратуру.");
            }
            appointment.setUser(existing.getUser());
            appointment.setStatus(existing.getStatus());
        }
        applyAppointmentType(appointment, serviceIds, doctorId, result);
        validateAppointment(appointment, result);
        validateDoctorAvailability(appointment, id, result);
        if (result.hasErrors()) {
            appointment.setIdAppointment(id);
            model.addAttribute("appointment", appointment);
            addFormOptions(model, principal);
            return "appointmentForm";
        }
        appointment.setIdAppointment(id);
        appointmentService.saveBooking(appointment, null, hasAuthority(principal, "ADMIN"), false);
        return "redirect:/appointments";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Integer id, @AuthenticationPrincipal UserDetails principal) {
        ensureOwnerOrStaff(appointmentService.findById(id), principal);
        appointmentService.deleteById(id);
        return "redirect:/appointments";
    }

    @PostMapping("/{id}/cancel")
    public Object cancel(@PathVariable Integer id, @AuthenticationPrincipal UserDetails principal,
                         @RequestHeader(value = "X-Requested-With", required = false) String requestedWith) {
        ensureOwnerOrStaff(appointmentService.findById(id), principal);
        try {
            appointmentService.cancel(id);
        } catch (IllegalStateException ex) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, ex.getMessage());
        }
        if ("XMLHttpRequest".equals(requestedWith)) return org.springframework.http.ResponseEntity.ok(
                java.util.Map.of("message", "Запись отменена; слот снова доступен."));
        return "redirect:/appointments";
    }

    @GetMapping("/availability")
    @ResponseBody
    public AppointmentService.SlotAvailability availability(
            @RequestParam Integer doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (userService.findById(doctorId) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Врач не найден.");
        }
        return appointmentService.availability(doctorId, date);
    }

    @PostMapping("/holds")
    @ResponseBody
    @PreAuthorize("hasAuthority('PATIENT')")
    public AppointmentService.HoldResult reserveSlot(@RequestBody ReservationRequest request,
                                                       @AuthenticationPrincipal UserDetails principal) {
        if (request.doctorId() == null || request.appointmentDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выберите врача, дату и время приёма.");
        }
        com.example.medicalsystem.model.User patient = userService.findByUsername(principal.getUsername());
        return appointmentService.reserveSlot(patient.getIdUser(), request.doctorId(), request.appointmentDate());
    }

    public record ReservationRequest(Integer doctorId, LocalDateTime appointmentDate) { }

    @GetMapping("/availability/month")
    @ResponseBody
    public java.util.Map<LocalDate, Boolean> monthAvailability(
            @RequestParam Integer doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        if (userService.findById(doctorId) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Врач не найден.");
        }
        return appointmentService.monthAvailability(doctorId, month);
    }

    private boolean isPatientOnly(UserDetails principal) {
        boolean patient = principal.getAuthorities().stream().anyMatch(a -> "PATIENT".equals(a.getAuthority()));
        boolean staff = principal.getAuthorities().stream()
                .anyMatch(a -> "ADMIN".equals(a.getAuthority()) || "DOCTOR".equals(a.getAuthority()));
        return patient && !staff;
    }

    private boolean hasAuthority(UserDetails principal, String authority) {
        return principal.getAuthorities().stream().anyMatch(item -> authority.equals(item.getAuthority()));
    }

    private void ensureOwnerOrStaff(Appointment appointment, UserDetails principal) {
        if (appointment == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Запись не найдена");
        }
        com.example.medicalsystem.model.User currentUser = userService.findByUsername(principal.getUsername());
        boolean admin = principal.getAuthorities().stream().anyMatch(a -> "ADMIN".equals(a.getAuthority()));
        boolean doctor = principal.getAuthorities().stream().anyMatch(a -> "DOCTOR".equals(a.getAuthority()));
        boolean patient = principal.getAuthorities().stream().anyMatch(a -> "PATIENT".equals(a.getAuthority()));
        boolean allowed = admin
                || (doctor && appointment.getDoctor() != null && currentUser.getIdUser().equals(appointment.getDoctor().getIdUser()))
                || (patient && appointment.getUser() != null && currentUser.getIdUser().equals(appointment.getUser().getIdUser()));
        if (!allowed) {
            throw new AccessDeniedException("Недостаточно прав для этой записи");
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

    private java.util.List<com.example.medicalsystem.model.User> findDoctors(Integer specialtyId) {
        java.util.List<com.example.medicalsystem.model.User> doctors = findDoctors();
        if (specialtyId == null) return doctors;
        return doctors.stream().filter(doctor -> doctor.getSpecialty() != null
                && specialtyId.equals(doctor.getSpecialty().getIdSpecialty())).toList();
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

    private void applyAppointmentType(Appointment appointment, java.util.List<Integer> serviceIds,
                                      Integer doctorId, BindingResult result) {
        AppointmentType type = appointment.getType() == null ? AppointmentType.DOCTOR_VISIT : appointment.getType();
        appointment.setType(type);
        java.util.List<MedicalServiceEntity> selected = findServices(serviceIds);
        long requestedCount = serviceIds == null ? 0 : serviceIds.stream().filter(java.util.Objects::nonNull).distinct().count();
        if (selected.size() != requestedCount) {
            result.reject("appointment.service.invalid", "Одна или несколько выбранных услуг не найдены.");
        }
        if (type == AppointmentType.DOCTOR_VISIT) {
            if (!selected.isEmpty()) result.reject("appointment.doctor.services", "Для приёма врача нельзя выбирать услуги.");
            appointment.setServices(java.util.List.of());
            assignDoctor(appointment, doctorId, result);
            if (appointment.getDepartment() == null) {
                result.rejectValue("department", "appointment.department.required", "Выберите отделение.");
            } else if (appointment.getDoctor() != null && (appointment.getDoctor().getDepartment() == null
                    || !appointment.getDepartment().getIdDepartment().equals(
                    appointment.getDoctor().getDepartment().getIdDepartment()))) {
                result.rejectValue("doctor", "appointment.doctor.department", "Выбранный врач не относится к этому отделению.");
            }
        } else {
            if (doctorId != null || appointment.getDepartment() != null) {
                result.reject("appointment.service.fields", "Для записи на услуги нельзя указывать врача или отделение.");
            }
            appointment.setDoctor(null);
            appointment.setDepartment(null);
            appointment.setServices(selected);
            if (selected.isEmpty()) result.reject("appointment.service.required", "Выберите хотя бы одну услугу.");
        }
    }

    private void addFormOptions(Model model, UserDetails principal) {
        com.example.medicalsystem.model.User currentUser = userService.findByUsername(principal.getUsername());
        model.addAttribute("users", isPatientOnly(principal) ? java.util.List.of(currentUser) : userService.findAll());
        model.addAttribute("doctors", findDoctors());
        model.addAttribute("departments", departmentService.findAll());
        model.addAttribute("services", serviceEntityService.findAll());
        model.addAttribute("isPatientOnly", isPatientOnly(principal));
    }

    private void addPagination(Model model, Page<?> page) {
        model.addAttribute("currentPage", page.getNumber() + 1);
        model.addAttribute("totalPages", Math.max(page.getTotalPages(), 1));
        model.addAttribute("hasNext", page.hasNext());
        model.addAttribute("hasPrevious", page.hasPrevious());
    }
}
