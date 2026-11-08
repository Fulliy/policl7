package com.example.medicalsystem.controller;

import com.example.medicalsystem.model.MedicalRecord;
import com.example.medicalsystem.service.MedicalRecordService;
import com.example.medicalsystem.service.AppointmentService;
import com.example.medicalsystem.service.UserService;
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
@PreAuthorize("hasAnyAuthority('ADMIN', 'DOCTOR')")
@Tag(name = "Медицинские записи", description = "История болезни пациентов")
public class MedicalRecordController {

    private final MedicalRecordService recordService;
    private final AppointmentService appointmentService;
    private final UserService userService;

    public MedicalRecordController(MedicalRecordService recordService,
                                   AppointmentService appointmentService, UserService userService) {
        this.recordService = recordService;
        this.appointmentService = appointmentService;
        this.userService = userService;
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
            ensureCompletedAppointment(record);
        }
        model.addAttribute("medicalRecord", record);
        model.addAttribute("appointments", appointmentsFor(principal));
        return "medicalRecordForm";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("medicalRecord") MedicalRecord record,
                       BindingResult result,
                       Model model, @AuthenticationPrincipal UserDetails principal) {
        ensureDoctorOwns(record, principal);
        if (result.hasErrors()) {
            model.addAttribute("medicalRecord", record);
            model.addAttribute("appointments", appointmentsFor(principal));
            return "medicalRecordForm";
        }
        recordService.save(record);
        return isDoctor(principal) ? "redirect:/doctor/patients/" + record.getAppointment().getUser().getIdUser() : "redirect:/medicalRecords";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Integer id, Model model, @AuthenticationPrincipal UserDetails principal) {
        MedicalRecord record = recordService.findById(id);
        ensureDoctorOwns(record, principal);
        model.addAttribute("medicalRecord", record);
        model.addAttribute("appointments", appointmentsFor(principal));
        return "medicalRecordForm";
    }

    @PostMapping("/update")
    public String update(@RequestParam Integer id,
                         @Valid @ModelAttribute("medicalRecord") MedicalRecord record,
                         BindingResult result,
                         Model model, @AuthenticationPrincipal UserDetails principal) {
        MedicalRecord previous = recordService.findById(id);
        ensureDoctorOwns(previous, principal);
        if (isDoctor(principal)) {
            ensureCompletedAppointment(previous);
            record.setAppointment(previous.getAppointment());
        }
        if (result.hasErrors()) {
            record.setIdRecord(id);
            model.addAttribute("medicalRecord", record);
            model.addAttribute("appointments", appointmentsFor(principal));
            return "medicalRecordForm";
        }
        record.setIdRecord(id);
        recordService.save(record);
        return isDoctor(principal) ? "redirect:/doctor/patients/" + record.getAppointment().getUser().getIdUser() : "redirect:/medicalRecords";
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
        return appointmentService.findByDoctor(doctor.getIdUser());
    }

    private void ensureDoctorOwns(MedicalRecord record, UserDetails principal) {
        if (!isDoctor(principal)) return;
        User doctor = userService.findByUsername(principal.getUsername());
        if (record == null || record.getAppointment() == null || record.getAppointment().getDoctor() == null
                || !doctor.getIdUser().equals(record.getAppointment().getDoctor().getIdUser())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "Доступ разрешён только к медкартам ваших пациентов.");
        }
    }

    private static boolean isDoctor(UserDetails principal) {
        return principal != null && principal.getAuthorities().stream().anyMatch(authority -> "DOCTOR".equals(authority.getAuthority()));
    }

    private void ensureCompletedAppointment(MedicalRecord record) {
        if (record == null || record.getAppointment() == null
                || !"completed".equalsIgnoreCase(record.getAppointment().getStatus())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "Медицинский итог можно заполнить только после завершения приёма.");
        }
    }

    private void addPagination(Model model, Page<?> page) {
        model.addAttribute("currentPage", page.getNumber() + 1);
        model.addAttribute("totalPages", Math.max(page.getTotalPages(), 1));
        model.addAttribute("hasNext", page.hasNext());
        model.addAttribute("hasPrevious", page.hasPrevious());
    }
}
