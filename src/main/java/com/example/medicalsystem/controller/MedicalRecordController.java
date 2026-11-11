package com.example.medicalsystem.controller;

import com.example.medicalsystem.model.MedicalRecord;
import com.example.medicalsystem.service.MedicalRecordService;
import com.example.medicalsystem.service.AppointmentService;
import com.example.medicalsystem.service.UserService;
import com.example.medicalsystem.service.PrescriptionService;
import com.example.medicalsystem.service.ServiceEntityService;
import com.example.medicalsystem.model.Prescription;
import com.example.medicalsystem.model.User;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/medicalRecords")
@PreAuthorize("hasAnyAuthority('ADMIN', 'ROLE_ADMIN', 'DOCTOR', 'ROLE_DOCTOR')")
@Tag(name = "Медицинские записи", description = "История болезни пациентов")
public class MedicalRecordController {
    private static final java.util.logging.Logger AUDIT_LOG = java.util.logging.Logger.getLogger(MedicalRecordController.class.getName());

    private final MedicalRecordService recordService;
    private final AppointmentService appointmentService;
    private final UserService userService;
    private final PrescriptionService prescriptionService;
    private final ServiceEntityService serviceEntityService;

    public MedicalRecordController(MedicalRecordService recordService,
                                   AppointmentService appointmentService, UserService userService,
                                   PrescriptionService prescriptionService, ServiceEntityService serviceEntityService) {
        this.recordService = recordService;
        this.appointmentService = appointmentService;
        this.userService = userService;
        this.prescriptionService = prescriptionService;
        this.serviceEntityService = serviceEntityService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page,
                       @RequestParam(required = false) String searchField,
                       @RequestParam(required = false) String searchValue,
                       Model model, @AuthenticationPrincipal UserDetails principal) {
        if (isDoctor(principal)) return "redirect:/doctor/patients";
        Page<MedicalRecord> records = recordService.search(searchField, searchValue, page);

        model.addAttribute("medicalRecords", records.getContent());
        addPagination(model, records);
        model.addAttribute("searchField", searchField);
        model.addAttribute("searchValue", searchValue);

        return "medicalRecords";
    }

    @GetMapping("/new")
    public String showCreateForm(@RequestParam(required = false) Integer appointmentId, Model model,
                                 @AuthenticationPrincipal UserDetails principal) {
        MedicalRecord record = new MedicalRecord();
        if (appointmentId != null) {
            record.setAppointment(appointmentService.findById(appointmentId));
            ensureDoctorOwns(record, principal);
            if (isDoctor(principal)) {
                ensureAppointmentCanReceiveRecord(record);
                var existingRecord = recordService.findByAppointment(appointmentId).stream().findFirst().orElse(null);
                if (existingRecord != null) return "redirect:/medicalRecords/" + existingRecord.getIdRecord() + "/edit";
            }
        }
        model.addAttribute("medicalRecord", record);
        model.addAttribute("appointments", appointmentsFor(principal));
        model.addAttribute("isDoctor", isDoctor(principal));
        model.addAttribute("prescriptions", java.util.List.<Prescription>of());
        model.addAttribute("services", serviceEntityService.findAll());
        return "medicalRecordForm";
    }

    @PostMapping("/save")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public String save(@RequestParam(required = false) Integer appointmentId,
                       @RequestParam(required = false) String medicationName,
                       @RequestParam(required = false) String dosage,
                       @RequestParam(required = false) String frequency,
                       @RequestParam(required = false) Integer durationDays,
                       @RequestParam(required = false) Integer serviceId,
                       @Valid @ModelAttribute("medicalRecord") MedicalRecord record,
                       BindingResult result,
                       Model model, @AuthenticationPrincipal UserDetails principal) {
        if (isDoctor(principal) && record.getAppointment() == null && appointmentId != null) {
            record.setAppointment(appointmentService.findById(appointmentId));
        }
        ensureDoctorOwns(record, principal);
        if (isDoctor(principal)) {
            ensureAppointmentCanReceiveRecord(record);
            if ("pending".equalsIgnoreCase(record.getAppointment().getStatus())) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                        "Сначала подтвердите приём или завершите его.");
            }
            if (recordService.findByAppointment(record.getAppointment().getIdAppointment()).size() > 0) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                        "Для этого приёма медицинский итог уже создан. Откройте его для редактирования.");
            }
            record.setCreatedDate(java.time.LocalDateTime.now(java.time.ZoneId.of("Europe/Moscow")));
        }
        if (medicationName != null && !medicationName.isBlank()
                && (medicationName.trim().length() < 2 || medicationName.trim().length() > 100
                || (dosage != null && dosage.length() > 50) || (frequency != null && frequency.length() > 50)
                || durationDays == null || durationDays < 1 || durationDays > 365)) {
            result.reject("prescription.invalid", "Для назначения укажите длительность от 1 до 365 дней.");
        }
        if (result.hasErrors()) {
            model.addAttribute("medicalRecord", record);
            model.addAttribute("appointments", appointmentsFor(principal));
            model.addAttribute("isDoctor", isDoctor(principal));
            model.addAttribute("prescriptions", java.util.List.<Prescription>of());
            model.addAttribute("services", serviceEntityService.findAll());
            model.addAttribute("prescriptionError", result.getGlobalErrors().stream().findFirst().map(error -> error.getDefaultMessage()).orElse(null));
            if (medicationName != null) model.addAttribute("submittedMedicationName", medicationName);
            if (dosage != null) model.addAttribute("submittedDosage", dosage);
            if (frequency != null) model.addAttribute("submittedFrequency", frequency);
            if (durationDays != null) model.addAttribute("submittedDurationDays", durationDays);
            if (serviceId != null) model.addAttribute("submittedServiceId", serviceId);
            return "medicalRecordForm";
        }
        if (!isDoctor(principal) && record.getCreatedDate() == null) {
            record.setCreatedDate(java.time.LocalDateTime.now(java.time.ZoneId.of("Europe/Moscow")));
        }
        recordService.save(record);
        auditRecordChange("CREATE", record, principal);
        if (isDoctor(principal) && medicationName != null && !medicationName.isBlank()) {
            Prescription prescription = new Prescription();
            prescription.setMedicationName(medicationName.trim());
            prescription.setDosage(dosage == null ? null : dosage.trim());
            prescription.setFrequency(frequency == null ? null : frequency.trim());
            prescription.setDurationDays(durationDays);
            prescription.setMedicalRecord(record);
            prescription.setService(serviceId == null ? null : serviceEntityService.findById(serviceId));
            prescriptionService.save(prescription);
        }
        return isDoctor(principal) ? "redirect:/doctor/patients/" + record.getAppointment().getUser().getIdUser()
                + "?appointmentId=" + record.getAppointment().getIdAppointment() : "redirect:/medicalRecords";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Integer id, Model model, @AuthenticationPrincipal UserDetails principal) {
        MedicalRecord record = recordService.findById(id);
        ensureDoctorOwns(record, principal);
        if (isDoctor(principal)) ensureAppointmentCanReceiveRecord(record);
        model.addAttribute("medicalRecord", record);
        model.addAttribute("appointments", appointmentsFor(principal));
        model.addAttribute("isDoctor", isDoctor(principal));
        model.addAttribute("prescriptions", prescriptionService.findByMedicalRecord(record.getIdRecord()));
        model.addAttribute("services", serviceEntityService.findAll());
        return "medicalRecordForm";
    }

    @PostMapping("/update")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public String update(@RequestParam Integer id,
                         @RequestParam(required = false) String medicationName,
                         @RequestParam(required = false) String dosage,
                         @RequestParam(required = false) String frequency,
                         @RequestParam(required = false) Integer durationDays,
                         @RequestParam(required = false) Integer serviceId,
                         @Valid @ModelAttribute("medicalRecord") MedicalRecord record,
                         BindingResult result,
                         Model model, @AuthenticationPrincipal UserDetails principal) {
        MedicalRecord previous = recordService.findById(id);
        ensureDoctorOwns(previous, principal);
        if (isDoctor(principal)) {
            ensureAppointmentCanReceiveRecord(previous);
            record.setAppointment(previous.getAppointment());
            record.setCreatedDate(previous.getCreatedDate());
        }
        if (isDoctor(principal) && medicationName != null && !medicationName.isBlank()
                && (medicationName.trim().length() < 2 || medicationName.trim().length() > 100
                || (dosage != null && dosage.length() > 50) || (frequency != null && frequency.length() > 50)
                || durationDays == null || durationDays < 1 || durationDays > 365)) {
            result.reject("prescription.invalid", "Для назначения укажите длительность от 1 до 365 дней.");
        }
        if (result.hasErrors()) {
            record.setIdRecord(id);
            model.addAttribute("medicalRecord", record);
            model.addAttribute("appointments", appointmentsFor(principal));
            model.addAttribute("isDoctor", isDoctor(principal));
            model.addAttribute("prescriptions", prescriptionService.findByMedicalRecord(id));
            model.addAttribute("services", serviceEntityService.findAll());
            model.addAttribute("prescriptionError", result.getGlobalErrors().stream().findFirst().map(error -> error.getDefaultMessage()).orElse(null));
            if (medicationName != null) model.addAttribute("submittedMedicationName", medicationName);
            if (dosage != null) model.addAttribute("submittedDosage", dosage);
            if (frequency != null) model.addAttribute("submittedFrequency", frequency);
            if (durationDays != null) model.addAttribute("submittedDurationDays", durationDays);
            if (serviceId != null) model.addAttribute("submittedServiceId", serviceId);
            return "medicalRecordForm";
        }
        record.setIdRecord(id);
        record.setAppointment(previous.getAppointment());
        record.setCreatedDate(previous.getCreatedDate());
        recordService.save(record);
        auditRecordChange("UPDATE", record, principal);
        if (isDoctor(principal) && medicationName != null && !medicationName.isBlank()) {
            Prescription existing = prescriptionService.findByMedicalRecord(id).stream().findFirst().orElse(null);
            if (existing == null) existing = new Prescription();
            existing.setMedicationName(medicationName.trim());
            existing.setDosage(dosage == null ? null : dosage.trim());
            existing.setFrequency(frequency == null ? null : frequency.trim());
            existing.setDurationDays(durationDays);
            existing.setMedicalRecord(record);
            existing.setService(serviceId == null ? null : serviceEntityService.findById(serviceId));
            prescriptionService.save(existing);
        }
        return isDoctor(principal) ? "redirect:/doctor/patients/" + record.getAppointment().getUser().getIdUser()
                + "?appointmentId=" + record.getAppointment().getIdAppointment() : "redirect:/medicalRecords";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Integer id, @AuthenticationPrincipal UserDetails principal) {
        MedicalRecord record = recordService.findById(id);
        ensureDoctorOwns(record, principal);
        recordService.deleteById(id);
        return isDoctor(principal) ? "redirect:/doctor/patients/" + record.getAppointment().getUser().getIdUser() : "redirect:/medicalRecords";
    }

    private java.util.List<com.example.medicalsystem.model.Appointment> appointmentsFor(UserDetails principal) {
        if (!isDoctor(principal)) return appointmentService.findAll();
        User doctor = userService.findByUsername(principal.getUsername());
        return appointmentService.findByDoctor(doctor.getIdUser()).stream()
                .filter(appointment -> appointment.getAppointmentDate() != null
                        && !appointment.getAppointmentDate().isAfter(java.time.LocalDateTime.now(java.time.ZoneId.of("Europe/Moscow"))))
                .filter(appointment -> "completed".equalsIgnoreCase(appointment.getStatus())
                        || "pending".equalsIgnoreCase(appointment.getStatus())
                        || "confirmed".equalsIgnoreCase(appointment.getStatus()))
                .toList();
    }

    private void ensureDoctorOwns(MedicalRecord record, UserDetails principal) {
        if (!isDoctor(principal)) return;
        User doctor = userService.findByUsername(principal.getUsername());
        if (record == null || record.getAppointment() == null || record.getAppointment().getUser() == null
                || record.getAppointment().getDoctor() == null
                || !doctor.getIdUser().equals(record.getAppointment().getDoctor().getIdUser())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "Доступ разрешён только к медкартам ваших пациентов.");
        }
    }

    private void auditRecordChange(String action, MedicalRecord record, UserDetails principal) {
        User actor = principal == null ? null : userService.findByUsername(principal.getUsername());
        Integer appointmentId = record == null || record.getAppointment() == null
                ? null : record.getAppointment().getIdAppointment();
        AUDIT_LOG.info(() -> "medical_record_audit action=" + action
                + " recordId=" + (record == null ? null : record.getIdRecord())
                + " appointmentId=" + appointmentId
                + " actorId=" + (actor == null ? null : actor.getIdUser()));
    }

    private static boolean isDoctor(UserDetails principal) {
        return principal != null && principal.getAuthorities().stream().anyMatch(authority ->
                "DOCTOR".equals(authority.getAuthority()) || "ROLE_DOCTOR".equals(authority.getAuthority()));
    }

    private void ensureAppointmentCanReceiveRecord(MedicalRecord record) {
        if (record == null || record.getAppointment() == null
                || record.getAppointment().getUser() == null
                || record.getAppointment().getAppointmentDate() == null
                || record.getAppointment().getAppointmentDate().isAfter(java.time.LocalDateTime.now(java.time.ZoneId.of("Europe/Moscow")))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "Заполнить медицинский итог можно после времени приёма.");
        }
        String status = record.getAppointment().getStatus();
        if (!"completed".equalsIgnoreCase(status) && !"confirmed".equalsIgnoreCase(status)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "Для этой записи нельзя добавить медицинский итог.");
        }
    }

    private void addPagination(Model model, Page<?> page) {
        model.addAttribute("currentPage", page.getNumber() + 1);
        model.addAttribute("totalPages", Math.max(page.getTotalPages(), 1));
        model.addAttribute("hasNext", page.hasNext());
        model.addAttribute("hasPrevious", page.hasPrevious());
    }
}
