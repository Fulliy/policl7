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
import org.springframework.beans.factory.annotation.Autowired;
import com.example.medicalsystem.repository.AppointmentGoalRepository;
import com.example.medicalsystem.repository.ClinicBranchRepository;
import com.example.medicalsystem.repository.SpecialtyRepository;
import com.example.medicalsystem.config.SchedulingProperties;
import com.example.medicalsystem.model.AppointmentGoal;
import com.example.medicalsystem.model.ClinicBranch;
import com.example.medicalsystem.model.User;
import com.example.medicalsystem.model.Department;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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
    @Autowired(required = false) private AppointmentGoalRepository goalRepository;
    @Autowired(required = false) private ClinicBranchRepository branchRepository;
    @Autowired(required = false) private SpecialtyRepository specialtyRepository;
    @Autowired(required = false) private SchedulingProperties schedulingProperties;

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
        model.addAttribute("now", LocalDateTime.now(ZoneId.of("Europe/Moscow")));
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
            if (goalRepository != null && branchRepository != null && specialtyRepository != null) {
                model.addAttribute("appointmentGoals", goalRepository.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
                        .filter(goal -> !goal.isPaid())
                        .filter(goal -> !"Единое окно".equalsIgnoreCase(goal.getName())).toList());
                model.addAttribute("bookingSpecialties", specialtyRepository.findAll());
                model.addAttribute("services", serviceEntityService.findAll());
                model.addAttribute("isPatientOnly", true);
                model.addAttribute("patient", userService.findByUsername(principal.getUsername()));
                model.addAttribute("orviAdvanceDays", schedulingProperties == null ? 7 : Math.min(schedulingProperties.getOrviAdvanceDays(), 7));
                model.addAttribute("bookingPreviewDays", schedulingProperties == null ? 30 : schedulingProperties.getBookingPreviewDays());
                model.addAttribute("initialGoalId", null);
                return "patientBookingWizard";
            }
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

    @GetMapping("/booking/doctors")
    @PreAuthorize("hasAuthority('PATIENT')")
    public String showBookingDoctors(@RequestParam Integer goal, Model model,
                                    @AuthenticationPrincipal UserDetails principal) {
        if (goalRepository == null || branchRepository == null || specialtyRepository == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Каталог целей записи недоступен.");
        }
        model.addAttribute("appointmentGoals", goalRepository.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .filter(item -> !item.isPaid())
                .filter(item -> !"Единое окно".equalsIgnoreCase(item.getName())).toList());
        model.addAttribute("services", serviceEntityService.findAll());
        model.addAttribute("isPatientOnly", true);
        model.addAttribute("patient", userService.findByUsername(principal.getUsername()));
        model.addAttribute("orviAdvanceDays", schedulingProperties == null ? 7 : Math.min(schedulingProperties.getOrviAdvanceDays(), 7));
        model.addAttribute("bookingPreviewDays", schedulingProperties == null ? 30 : schedulingProperties.getBookingPreviewDays());
        model.addAttribute("initialGoalId", goal);
        return "patientBookingWizard";
    }

    @PostMapping("/save")
    @Operation(summary = "Создание записи на приём")
    public Object save(@ModelAttribute("appointment") Appointment appointment,
                       BindingResult result,
                       @RequestParam(name = "serviceIds", required = false) java.util.List<Integer> serviceIds,
                       @RequestParam(name = "doctorId", required = false) Integer doctorId,
                       @RequestParam(name = "departmentId", required = false) Integer departmentId,
                       @RequestParam(name = "holdId", required = false) Integer holdId,
                       @RequestParam(name = "goalId", required = false) Integer goalId,
                       @RequestParam(name = "branchId", required = false) Integer branchId,
                       @RequestParam(name = "paidConsent", defaultValue = "false") boolean paidConsent,
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
        if (departmentId != null) appointment.setDepartment(departmentService.findById(departmentId));
        // В пошаговой записи отделение не выбирается отдельно. Подставляем
        // первое отделение врача до общей валидации формы, иначе applyAppointmentType
        // успевает добавить ошибку «Выберите отделение».
        if (isPatientOnly(principal) && appointment.getDepartment() == null
                && doctorId != null && appointment.getType() == AppointmentType.DOCTOR_VISIT) {
            User selectedDoctor = userService.findById(doctorId);
            if (selectedDoctor != null) {
                List<Department> assignedDepartments = selectedDoctor.getDoctorDepartments();
                appointment.setDepartment(assignedDepartments != null && !assignedDepartments.isEmpty()
                        ? assignedDepartments.get(0) : selectedDoctor.getDepartment());
            }
        }
        applyAppointmentType(appointment, serviceIds, doctorId, result);
        assignBookingCatalog(appointment, goalId, branchId, paidConsent, result, principal);
        validateAppointment(appointment, result);
        validateDoctorAvailability(appointment, null, result);
        if (result.hasErrors()) {
            if (ajax) return org.springframework.http.ResponseEntity.badRequest().body(
                    java.util.Map.of("message", result.getAllErrors().get(0).getDefaultMessage()));
            model.addAttribute("appointment", appointment);
            addFormOptions(model, principal);
            return "appointmentForm";
        }
        try {
            appointmentService.saveBooking(appointment, holdId, administrator, administrator && newAppointment);
        } catch (ResponseStatusException exception) {
            if (ajax) return org.springframework.http.ResponseEntity.status(exception.getStatusCode()).body(
                    java.util.Map.of("message", exception.getReason() == null ? "Не удалось создать запись." : exception.getReason()));
            throw exception;
        }
        if (ajax) return org.springframework.http.ResponseEntity.ok(java.util.Map.of(
                "message", "Запись создана.", "redirectUrl", "/appointments"));
        return "redirect:/appointments";
    }

    @GetMapping("/{id}/reschedule")
    @PreAuthorize("hasAuthority('PATIENT')")
    public String showRescheduleForm(@PathVariable Integer id, Model model,
                                     @AuthenticationPrincipal UserDetails principal) {
        Appointment appointment = appointmentService.findById(id);
        ensureOwnerOrStaff(appointment, principal);
        ensurePatientCanModify(appointment, principal);
        if (appointment.getDoctor() == null || appointment.getType() == AppointmentType.SERVICE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Перенос через календарь доступен только для приёма у врача. Для переноса услуги обратитесь в регистратуру.");
        }
        model.addAttribute("appointment", appointment);
        model.addAttribute("now", LocalDateTime.now(ZoneId.of("Europe/Moscow")));
        model.addAttribute("bookingPreviewDays", schedulingProperties == null ? 30 : schedulingProperties.getBookingPreviewDays());
        model.addAttribute("rescheduleDeadlineHours", schedulingProperties == null ? 24 : schedulingProperties.getCancellationDeadlineHours());
        return "patientReschedule";
    }

    @PostMapping("/{id}/reschedule")
    @ResponseBody
    @PreAuthorize("hasAuthority('PATIENT')")
    public Object reschedule(@PathVariable Integer id,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime appointmentDate,
                             @AuthenticationPrincipal UserDetails principal) {
        Appointment existing = appointmentService.findById(id);
        ensureOwnerOrStaff(existing, principal);
        ensurePatientCanModify(existing, principal);
        if (existing.getDoctor() == null || existing.getType() == AppointmentType.SERVICE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Эту запись нельзя перенести через календарь.");
        }
        com.example.medicalsystem.model.User patient = userService.findByUsername(principal.getUsername());
        appointmentService.reschedulePatientAppointment(id, patient.getIdUser(), appointmentDate);
        return java.util.Map.of("message", "Запись перенесена.", "redirectUrl", "/appointments");
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Integer id, Model model,
                               @AuthenticationPrincipal UserDetails principal) {
        Appointment appointment = appointmentService.findById(id);
        ensureOwnerOrStaff(appointment, principal);
        if (!hasAuthority(principal, "ADMIN") && !hasAuthority(principal, "DOCTOR")) ensurePatientCanModify(appointment, principal);
        if (hasAuthority(principal, "DOCTOR")) ensureDoctorOwnsAppointment(appointment, principal);
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
        if (!hasAuthority(principal, "ADMIN") && !hasAuthority(principal, "DOCTOR")) ensurePatientCanModify(existing, principal);
        if (hasAuthority(principal, "DOCTOR")) ensureDoctorOwnsAppointment(existing, principal);
        if (isPatientOnly(principal)) {
            if (existing.isRequiresReschedule()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Приём требует переноса, обратитесь в регистратуру.");
            }
            appointment.setUser(existing.getUser());
            appointment.setStatus(existing.getStatus());
        }
        applyAppointmentType(appointment, serviceIds, doctorId, result);
        if (existing.getGoal() != null) appointment.setGoal(existing.getGoal());
        if (existing.getBranch() != null) appointment.setBranch(existing.getBranch());
        validateAppointment(appointment, result);
        validateDoctorAvailability(appointment, id, result);
        if (result.hasErrors()) {
            appointment.setIdAppointment(id);
            model.addAttribute("appointment", appointment);
            addFormOptions(model, principal);
            return "appointmentForm";
        }
        appointment.setIdAppointment(id);
        if (hasAuthority(principal, "DOCTOR")) {
            appointment.setDoctor(existing.getDoctor());
            appointment.setUser(existing.getUser());
            appointment.setGoal(existing.getGoal());
            appointment.setBranch(existing.getBranch());
        }
        appointmentService.saveBooking(appointment, null, hasAuthority(principal, "ADMIN"), false);
        return "redirect:/appointments";
    }

    @PostMapping("/{id}/complete")
    public String complete(@PathVariable Integer id, @AuthenticationPrincipal UserDetails principal) {
        if (!hasAuthority(principal, "DOCTOR")) {
            throw new AccessDeniedException("Завершить приём может только лечащий врач.");
        }
        Appointment appointment = appointmentService.findById(id);
        ensureDoctorOwnsAppointment(appointment, principal);
        User doctor = userService.findByUsername(principal.getUsername());
        appointmentService.completeDoctorVisit(id, doctor.getIdUser());
        return "redirect:/doctor/patients/" + appointment.getUser().getIdUser() + "?appointmentId=" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Integer id, @AuthenticationPrincipal UserDetails principal) {
        Appointment appointment = appointmentService.findById(id);
        ensureOwnerOrStaff(appointment, principal);
        if (!hasAuthority(principal, "ADMIN")) {
            throw new AccessDeniedException("Удалить запись может только регистратура.");
        }
        appointmentService.deleteById(id);
        return "redirect:/appointments";
    }

    @PostMapping("/{id}/cancel")
    public Object cancel(@PathVariable Integer id, @AuthenticationPrincipal UserDetails principal,
                         @RequestHeader(value = "X-Requested-With", required = false) String requestedWith) {
        Appointment appointment = appointmentService.findById(id);
        ensureOwnerOrStaff(appointment, principal);
        try {
            if (isPatientOnly(principal)) {
                appointmentService.cancelByPatient(id);
            } else {
                appointmentService.cancelByStaff(id);
            }
        } catch (IllegalStateException ex) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, ex.getMessage());
        }
        if ("XMLHttpRequest".equals(requestedWith)) return org.springframework.http.ResponseEntity.ok(
                java.util.Map.of("message", isPatientOnly(principal)
                        ? "Запись удалена; слот снова доступен."
                        : "Запись отменена; слот снова доступен."));
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
        boolean patient = hasAuthority(principal, "PATIENT");
        boolean staff = hasAuthority(principal, "ADMIN") || hasAuthority(principal, "DOCTOR");
        return patient && !staff;
    }

    private void ensurePatientCanModify(Appointment appointment, UserDetails principal) {
        if (!isPatientOnly(principal)) return;
        boolean active = appointment != null && ("pending".equalsIgnoreCase(appointment.getStatus())
                || "confirmed".equalsIgnoreCase(appointment.getStatus()));
        boolean future = appointment != null && appointment.getAppointmentDate() != null
                && appointment.getAppointmentDate().isAfter(LocalDateTime.now(ZoneId.of("Europe/Moscow")));
        if (!active || !future) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Перенести можно только предстоящую активную запись.");
    }

    private boolean hasAuthority(UserDetails principal, String authority) {
        if (principal == null || principal.getAuthorities() == null) return false;
        return principal.getAuthorities().stream().anyMatch(item -> {
            String granted = item.getAuthority();
            return authority.equals(granted) || ("ROLE_" + authority).equals(granted);
        });
    }

    private void ensureDoctorOwnsAppointment(Appointment appointment, UserDetails principal) {
        User doctor = userService.findByUsername(principal.getUsername());
        if (appointment == null || appointment.getDoctor() == null || doctor == null
                || !doctor.getIdUser().equals(appointment.getDoctor().getIdUser())) {
            throw new AccessDeniedException("Врач может изменять только собственные приёмы.");
        }
    }

    private void ensureOwnerOrStaff(Appointment appointment, UserDetails principal) {
        if (appointment == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Запись не найдена");
        }
        com.example.medicalsystem.model.User currentUser = userService.findByUsername(principal.getUsername());
        boolean admin = hasAuthority(principal, "ADMIN");
        boolean doctor = hasAuthority(principal, "DOCTOR");
        boolean patient = hasAuthority(principal, "PATIENT");
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

    private void assignBookingCatalog(Appointment appointment, Integer goalId, Integer branchId,
                                      boolean paidConsent, BindingResult result, UserDetails principal) {
        if (!isPatientOnly(principal) || goalRepository == null || branchRepository == null) return;
        AppointmentGoal goal = goalId == null ? null : goalRepository.findById(goalId).orElse(null);
        if (goal == null || !goal.isActive()) {
            // Вкладка «Специальность» допускает запись без отдельной цели обращения.
            // Для неё всё равно проверяем врача и филиал на сервере.
            if (goalId == null && appointment.getDoctor() != null
                    && appointment.getType() == AppointmentType.DOCTOR_VISIT) {
                List<ClinicBranch> assigned = appointment.getDoctor().getBranches() == null
                        ? List.of() : appointment.getDoctor().getBranches();
                ClinicBranch branch = branchId == null
                        ? (assigned.isEmpty() ? null : assigned.get(0))
                        : branchRepository.findById(branchId).orElse(null);
                if (branchId != null && (branch == null || assigned.stream().noneMatch(item -> item.getId().equals(branchId)))) {
                    result.reject("appointment.branch.invalid", "Врач не принимает в выбранном филиале.");
                }
                appointment.setBranch(branch);
                return;
            }
            result.reject("appointment.goal.required", "Выберите цель обращения.");
            return;
        }
        if (goal.getScenarioType() == AppointmentGoal.ScenarioType.EXTERNAL
                || (goal.isPaid() && !paidConsent)) {
            result.reject("appointment.goal.consent", goal.isPaid()
                    ? "Подтвердите согласие с условиями платной услуги."
                    : "Эта цель оформляется через внешнюю организацию.");
            return;
        }
        if (goal.getScenarioType() == AppointmentGoal.ScenarioType.PAID_SERVICE
                && (appointment.getType() != AppointmentType.SERVICE || appointment.getServices() == null
                || appointment.getServices().isEmpty())) {
            result.reject("appointment.paid-service.required", "Для платной цели выберите услуги и время.");
        }
        appointment.setGoal(goal);
        if (appointment.getDoctor() == null) {
            if (branchId != null) result.reject("appointment.branch.doctor", "Для выбранного филиала требуется выбрать врача.");
            return;
        }
        List<ClinicBranch> assigned = appointment.getDoctor().getBranches() == null
                ? List.of() : appointment.getDoctor().getBranches();
        ClinicBranch branch = branchId == null
                ? (assigned.isEmpty() ? null : assigned.get(0))
                : branchRepository.findById(branchId).orElse(null);
        if (branchId != null && (branch == null || assigned.stream().noneMatch(item -> item.getId().equals(branchId)))) {
            result.reject("appointment.branch.invalid", "Врач не принимает в выбранном филиале.");
            return;
        }
        appointment.setBranch(branch);
        List<Department> departments = appointment.getDoctor().getDoctorDepartments() == null
                ? List.of() : appointment.getDoctor().getDoctorDepartments();
        if (!departments.isEmpty() && (appointment.getDepartment() == null || departments.stream()
                .noneMatch(item -> item.getIdDepartment().equals(appointment.getDepartment().getIdDepartment())))) {
            result.reject("appointment.department.invalid", "Врач не принимает в выбранном отделении.");
        }
        if (goal.getScenarioType() == AppointmentGoal.ScenarioType.ORVI_DUTY && !appointment.getDoctor().isOnDutyOrvi()) {
            result.reject("appointment.doctor.orvi", "Для этой цели доступны только дежурные врачи ОРВИ.");
        }
        // Все врачи с доступным расписанием подходят для внутренних целей записи.
        // Фильтр по дежурству остаётся только для специального сценария ОРВИ.
        if (goal.getScenarioType() == AppointmentGoal.ScenarioType.ORVI_DUTY && appointment.getAppointmentDate() != null
                && appointment.getAppointmentDate().isAfter(LocalDateTime.now(ZoneId.of("Europe/Moscow"))
                .plusDays(Math.min(schedulingProperties == null ? 7 : Math.min(schedulingProperties.getOrviAdvanceDays(), 7), 7)))) {
            result.reject("appointment.orvi.horizon", "Для дежурного врача ОРВИ запись доступна только на ближайшие 7 дней.");
        }
    }

    @GetMapping("/booking/specialties")
    @ResponseBody
    @PreAuthorize("hasAuthority('PATIENT')")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<BookingSpecialty> bookingSpecialties() {
        if (specialtyRepository == null) return List.of();
        return specialtyRepository.findAll().stream().filter(specialty -> matchingSpecialtyDoctors(specialty.getIdSpecialty()).stream()
                        .anyMatch(doctor -> !doctor.getBranches().isEmpty()))
                .map(specialty -> new BookingSpecialty(specialty.getIdSpecialty(), specialty.getSpecialtyName(),
                        specialtyIcon(specialty.getSpecialtyName()))).toList();
    }

    @GetMapping("/booking/goals/{goalId}/doctors")
    @ResponseBody
    @PreAuthorize("hasAuthority('PATIENT')")
    @org.springframework.transaction.annotation.Transactional
    public List<BookingDoctor> doctorsForGoal(@PathVariable Integer goalId) {
        AppointmentGoal goal = goalRepository == null ? null : goalRepository.findById(goalId).orElse(null);
        if (goal == null || !goal.isActive()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Цель обращения не найдена.");
        List<User> candidates = findDoctors();
        if (goal.getScenarioType() == AppointmentGoal.ScenarioType.EXTERNAL) return List.of();
        if (goal.getScenarioType() == AppointmentGoal.ScenarioType.ORVI_DUTY) {
            candidates = candidates.stream().filter(User::isOnDutyOrvi).toList();
        }
        int horizon = goal.getScenarioType() == AppointmentGoal.ScenarioType.ORVI_DUTY ? 3 : 30;
        return candidates.stream().filter(doctor -> doctor.getBranches() != null && !doctor.getBranches().isEmpty())
                .map(doctor -> bookingDoctor(doctor, horizon)).toList();
    }

    @GetMapping("/booking/specialties/{specialtyId}/doctors")
    @ResponseBody
    @PreAuthorize("hasAuthority('PATIENT')")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<BookingDoctor> doctorsForSpecialty(@PathVariable Integer specialtyId) {
        return findDoctors().stream().filter(doctor -> doctor.getSpecialty() != null
                        && specialtyId.equals(doctor.getSpecialty().getIdSpecialty()))
                .filter(doctor -> doctor.getBranches() != null && !doctor.getBranches().isEmpty())
                .map(doctor -> bookingDoctor(doctor, 30)).toList();
    }

    private List<User> matchingSpecialtyDoctors(Integer specialtyId) {
        return findDoctors().stream().filter(doctor -> doctor.getSpecialty() != null
                && specialtyId.equals(doctor.getSpecialty().getIdSpecialty())).toList();
    }

    private BookingDoctor bookingDoctor(User doctor, int horizonDays) {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Moscow"));
        LocalDate next = null;
        for (int day = 0; day <= horizonDays; day++) {
            LocalDate candidate = today.plusDays(day);
            if (appointmentService.availabilityWithoutNearestDate(doctor.getIdUser(), candidate).available()) { next = candidate; break; }
        }
        // Наличие рабочего расписания проверяется при выборе конкретного слота.
        // Если календарь ещё не настроен, всё равно показываем врача, чтобы
        // пациент не получал пустой список из-за предварительного фильтра.
        if (next == null) next = today;
        List<BookingBranch> locations = doctor.getBranches().stream().map(branch -> new BookingBranch(branch.getId(), branch.getName(), branch.getAddress()))
                .toList();
        List<BookingDepartment> doctorDepartments = doctor.getDoctorDepartments() == null || doctor.getDoctorDepartments().isEmpty()
                ? (doctor.getDepartment() == null ? List.of() : List.of(new BookingDepartment(doctor.getDepartment().getIdDepartment(), doctor.getDepartment().getDepartmentName())))
                : doctor.getDoctorDepartments().stream().map(department -> new BookingDepartment(department.getIdDepartment(), department.getDepartmentName())).toList();
        return new BookingDoctor(doctor.getIdUser(), doctor.getDisplayName(), doctor.getSpecialty() == null ? "Врач" : doctor.getSpecialty().getSpecialtyName(), next,
                doctorDepartments, locations);
    }

    private String specialtyIcon(String name) {
        String n = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        if (n.contains("терап")) return "plus";
        if (n.contains("хирург")) return "cross";
        if (n.contains("офтальм")) return "eye";
        if (n.contains("оторинолар")) return "ear";
        if (n.contains("уролог")) return "medical";
        return "stethoscope";
    }

    public record BookingSpecialty(Integer id, String name, String iconKey) { }
    public record BookingBranch(Integer id, String name, String address) { }
    public record BookingDepartment(Integer id, String name) { }
    public record BookingDoctor(Integer id, String name, String specialty, LocalDate nextDate,
                                List<BookingDepartment> departments, List<BookingBranch> branches) { }

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
            } else if (appointment.getDoctor() != null && !doctorHasDepartment(appointment.getDoctor(), appointment.getDepartment())) {
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

    private boolean doctorHasDepartment(com.example.medicalsystem.model.User doctor,
                                        com.example.medicalsystem.model.Department department) {
        if (doctor.getDoctorDepartments() != null && !doctor.getDoctorDepartments().isEmpty()) {
            return doctor.getDoctorDepartments().stream().anyMatch(item ->
                    item.getIdDepartment().equals(department.getIdDepartment()));
        }
        return doctor.getDepartment() != null
                && doctor.getDepartment().getIdDepartment().equals(department.getIdDepartment());
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
