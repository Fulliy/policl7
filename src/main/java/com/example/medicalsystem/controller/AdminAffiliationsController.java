package com.example.medicalsystem.controller;

import com.example.medicalsystem.model.PatientAffiliation;
import com.example.medicalsystem.model.ClinicBranch;
import java.util.List;
import com.example.medicalsystem.model.RoleEnum;
import com.example.medicalsystem.repository.PatientAffiliationRepository;
import com.example.medicalsystem.repository.ClinicBranchRepository;
import com.example.medicalsystem.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/patient-affiliations")
@PreAuthorize("hasAuthority('ADMIN')")
@Validated
public class AdminAffiliationsController {
    private final PatientAffiliationRepository affiliations;
    private final UserRepository users;
    private final ClinicBranchRepository branches;

    public AdminAffiliationsController(PatientAffiliationRepository affiliations, UserRepository users,
                                       ClinicBranchRepository branches) {
        this.affiliations = affiliations;
        this.users = users;
        this.branches = branches;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("affiliations", affiliations.findAll());
        model.addAttribute("patients", users.findAll().stream()
                .filter(user -> user.getRoles() != null && user.getRoles().contains(RoleEnum.PATIENT)).toList());
        model.addAttribute("doctors", users.findAll().stream()
                .filter(user -> user.getRoles() != null && user.getRoles().contains(RoleEnum.DOCTOR)).toList());
        model.addAttribute("branches", branches.findAllByOrderByNameAsc());
        model.addAttribute("statuses", PatientAffiliation.AffiliationStatus.values());
        return "adminAffiliations";
    }

    @PostMapping
    public Object save(@RequestParam @jakarta.validation.constraints.NotNull Integer patientId,
                       @RequestParam(required = false) Integer branchId,
                       @RequestParam(required = false) Integer doctorId,
                       @RequestParam(required = false) String medicalCenterName,
                       @RequestParam(required = false) String doctorName,
                       @RequestParam @jakarta.validation.constraints.NotNull java.time.LocalDate attachedAt,
                       @RequestParam @jakarta.validation.constraints.NotNull PatientAffiliation.AffiliationStatus status,
                       RedirectAttributes redirectAttributes) {
        try {
        var patient = users.findById(patientId).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Пациент не найден."));
        ClinicBranch branch = branchId == null ? null : branches.findById(branchId).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Выбранный медцентр не найден."));
        var selectedDoctor = doctorId == null ? null : users.findById(doctorId).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Выбранный врач не найден."));
        if (selectedDoctor != null && (selectedDoctor.getRoles() == null || !selectedDoctor.getRoles().contains(RoleEnum.DOCTOR))) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Выбранный пользователь не является врачом.");
        }
        String center = branch != null ? branch.getName() : medicalCenterName == null ? "" : medicalCenterName.trim();
        String doctor = selectedDoctor != null ? selectedDoctor.getDisplayName() : "";
        if (branch == null || center.isBlank())
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Выберите медцентр из списка.");
        if (status == PatientAffiliation.AffiliationStatus.ACTIVE
                && affiliations.existsByPatient_IdUserAndStatus(patientId, PatientAffiliation.AffiliationStatus.ACTIVE)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "У пациента уже есть активное прикрепление. Сначала завершите его, затем создайте новое.");
        }
        if (affiliations.existsByPatient_IdUserAndMedicalCenterNameAndDoctorNameAndAttachedAt(
                patientId, center, doctor, attachedAt)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "Такое прикрепление уже существует.");
        }
        PatientAffiliation affiliation = new PatientAffiliation();
        affiliation.setPatient(patient);
        affiliation.setMedicalCenterName(center);
        affiliation.setDoctorName(doctor);
        affiliation.setAttachedAt(attachedAt);
        affiliation.setStatus(status);
        affiliations.save(affiliation);
        return "redirect:/admin/patient-affiliations";
        } catch (org.springframework.web.server.ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("affiliationError",
                    exception.getReason() == null ? "Не удалось сохранить прикрепление." : exception.getReason());
            return "redirect:/admin/patient-affiliations";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Integer id) {
        affiliations.deleteById(id);
        return "redirect:/admin/patient-affiliations";
    }
}
