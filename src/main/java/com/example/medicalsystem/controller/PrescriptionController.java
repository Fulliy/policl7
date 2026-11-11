package com.example.medicalsystem.controller;

import com.example.medicalsystem.model.Prescription;
import com.example.medicalsystem.service.MedicalRecordService;
import com.example.medicalsystem.service.AppointmentService;
import com.example.medicalsystem.service.PrescriptionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.medicalsystem.service.ServiceEntityService;
import com.example.medicalsystem.service.UserService;
import com.example.medicalsystem.model.MedicalRecord;
import com.example.medicalsystem.model.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/prescriptions")
@PreAuthorize("hasAnyAuthority('ADMIN', 'DOCTOR')")
@Tag(name = "Назначения", description = "Назначения процедур и лекарств")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;
    private final MedicalRecordService recordService;
    private final ServiceEntityService serviceService;
    private final UserService userService;
    private final AppointmentService appointmentService;

    public PrescriptionController(PrescriptionService prescriptionService,
                                  MedicalRecordService recordService,
                                  ServiceEntityService serviceService,
                                  UserService userService,
                                  AppointmentService appointmentService) {
        this.prescriptionService = prescriptionService;
        this.recordService = recordService;
        this.serviceService = serviceService;
        this.userService = userService;
        this.appointmentService = appointmentService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page,
                       @RequestParam(required = false) String searchField,
                       @RequestParam(required = false) String searchValue,
                       Model model, @AuthenticationPrincipal UserDetails principal) {
        Page<Prescription> prescriptions;
        if (isDoctor(principal)) {
            User doctor = currentDoctor(principal);
            java.util.List<Integer> appointmentIds = appointmentService.findByDoctor(doctor.getIdUser()).stream()
                    .map(item -> item.getIdAppointment()).toList();
            java.util.List<Prescription> visible = recordService.findByAppointments(appointmentIds).stream()
                    .flatMap(record -> prescriptionService.findByMedicalRecord(record.getIdRecord()).stream())
                    .filter(item -> searchPrescription(item, searchField, searchValue)).toList();
            int pageIndex = Math.max(page - 1, 0);
            int from = Math.min(pageIndex * 10, visible.size());
            int to = Math.min(from + 10, visible.size());
            prescriptions = new org.springframework.data.domain.PageImpl<>(visible.subList(from, to),
                    org.springframework.data.domain.PageRequest.of(pageIndex, 10), visible.size());
        } else {
            prescriptions = prescriptionService.search(searchField, searchValue, page);
        }

        model.addAttribute("prescriptions", prescriptions.getContent());
        model.addAttribute("isDoctor", isDoctor(principal));
        addPagination(model, prescriptions);
        model.addAttribute("searchField", searchField);
        model.addAttribute("searchValue", searchValue);

        return "prescriptions";
    }

    @GetMapping("/new")
    public String showCreateForm(@RequestParam(required = false) Integer recordId, Model model,
                                 @AuthenticationPrincipal UserDetails principal) {
        Prescription prescription = new Prescription();
        if (recordId != null) {
            prescription.setMedicalRecord(recordService.findById(recordId));
            ensureDoctorOwnsRecord(prescription.getMedicalRecord(), principal);
        }
        model.addAttribute("prescription", prescription);
        model.addAttribute("medicalRecords", recordsVisibleTo(principal));
        model.addAttribute("services", serviceService.findAll());
        model.addAttribute("isDoctor", isDoctor(principal));
        return "prescriptionForm";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("prescription") Prescription prescription,
                       BindingResult result,
                       Model model, @AuthenticationPrincipal UserDetails principal) {
        ensureDoctorOwnsRecord(prescription.getMedicalRecord(), principal);
        if (result.hasErrors()) {
            model.addAttribute("prescription", prescription);
            model.addAttribute("medicalRecords", recordsVisibleTo(principal));
            model.addAttribute("services", serviceService.findAll());
            model.addAttribute("isDoctor", isDoctor(principal));
            return "prescriptionForm";
        }
        prescriptionService.save(prescription);
        return "redirect:/prescriptions";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Integer id, Model model,
                               @AuthenticationPrincipal UserDetails principal) {
        Prescription prescription = prescriptionService.findById(id);
        if (prescription == null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Назначение не найдено.");
        ensureDoctorOwnsRecord(prescription.getMedicalRecord(), principal);
        model.addAttribute("prescription", prescription);
        model.addAttribute("medicalRecords", recordsVisibleTo(principal));
        model.addAttribute("services", serviceService.findAll());
        model.addAttribute("isDoctor", isDoctor(principal));
        return "prescriptionForm";
    }

    @PostMapping("/update")
    public String update(@RequestParam Integer id,
                         @Valid @ModelAttribute("prescription") Prescription prescription,
                         BindingResult result,
                         Model model, @AuthenticationPrincipal UserDetails principal) {
        Prescription existing = prescriptionService.findById(id);
        if (existing == null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Назначение не найдено.");
        ensureDoctorOwnsRecord(existing.getMedicalRecord(), principal);
        ensureDoctorOwnsRecord(prescription.getMedicalRecord(), principal);
        if (result.hasErrors()) {
            prescription.setIdPrescription(id);
            model.addAttribute("prescription", prescription);
            model.addAttribute("medicalRecords", recordsVisibleTo(principal));
            model.addAttribute("services", serviceService.findAll());
            model.addAttribute("isDoctor", isDoctor(principal));
            return "prescriptionForm";
        }
        prescription.setIdPrescription(id);
        prescriptionService.save(prescription);
        return "redirect:/prescriptions";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Integer id, @AuthenticationPrincipal UserDetails principal) {
        Prescription prescription = prescriptionService.findById(id);
        if (prescription == null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Назначение не найдено.");
        ensureDoctorOwnsRecord(prescription.getMedicalRecord(), principal);
        prescriptionService.deleteById(id);
        return "redirect:/prescriptions";
    }

    private java.util.List<MedicalRecord> recordsVisibleTo(UserDetails principal) {
        if (!isDoctor(principal)) return recordService.findAll();
        User doctor = currentDoctor(principal);
        return recordService.findAll().stream().filter(record -> ownsRecord(record, doctor)).toList();
    }

    private boolean searchPrescription(Prescription prescription, String field, String value) {
        if (field == null || value == null || value.isBlank()) return true;
        String query = value.trim().toLowerCase(java.util.Locale.ROOT);
        if ("id".equals(field) || "idPrescription".equals(field)) {
            try { return prescription.getIdPrescription().equals(Integer.valueOf(query)); }
            catch (NumberFormatException exception) { return false; }
        }
        return (prescription.getMedicationName() != null && prescription.getMedicationName().toLowerCase(java.util.Locale.ROOT).contains(query))
                || (prescription.getDosage() != null && prescription.getDosage().toLowerCase(java.util.Locale.ROOT).contains(query))
                || (prescription.getFrequency() != null && prescription.getFrequency().toLowerCase(java.util.Locale.ROOT).contains(query));
    }

    private void ensureDoctorOwnsRecord(MedicalRecord record, UserDetails principal) {
        if (isDoctor(principal) && !ownsRecord(record, currentDoctor(principal))) {
            throw new AccessDeniedException("Врач может работать только с назначениями по собственным приёмам.");
        }
    }

    private boolean ownsRecord(MedicalRecord record, User doctor) {
        return record != null && record.getAppointment() != null && record.getAppointment().getDoctor() != null
                && doctor != null && doctor.getIdUser().equals(record.getAppointment().getDoctor().getIdUser());
    }

    private User currentDoctor(UserDetails principal) {
        return userService.findByUsername(principal.getUsername());
    }

    private boolean isDoctor(UserDetails principal) {
        return principal != null && principal.getAuthorities().stream().anyMatch(authority ->
                "DOCTOR".equals(authority.getAuthority()) || "ROLE_DOCTOR".equals(authority.getAuthority()));
    }

    private void addPagination(Model model, Page<?> page) {
        model.addAttribute("currentPage", page.getNumber() + 1);
        model.addAttribute("totalPages", Math.max(page.getTotalPages(), 1));
        model.addAttribute("hasNext", page.hasNext());
        model.addAttribute("hasPrevious", page.hasPrevious());
    }
}
