package com.example.medicalsystem.controller;

import com.example.medicalsystem.model.PatientAffiliation;
import com.example.medicalsystem.model.RoleEnum;
import com.example.medicalsystem.repository.PatientAffiliationRepository;
import com.example.medicalsystem.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/patient-affiliations")
@PreAuthorize("hasAuthority('ADMIN')")
@Validated
public class AdminAffiliationsController {
    private final PatientAffiliationRepository affiliations;
    private final UserRepository users;

    public AdminAffiliationsController(PatientAffiliationRepository affiliations, UserRepository users) {
        this.affiliations = affiliations;
        this.users = users;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("affiliations", affiliations.findAll());
        model.addAttribute("patients", users.findAll().stream()
                .filter(user -> user.getRoles() != null && user.getRoles().contains(RoleEnum.PATIENT)).toList());
        model.addAttribute("statuses", PatientAffiliation.AffiliationStatus.values());
        return "adminAffiliations";
    }

    @PostMapping
    public String save(@RequestParam @jakarta.validation.constraints.NotNull Integer patientId,
                       @RequestParam @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 200) String medicalCenterName,
                       @RequestParam @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 150) String doctorName,
                       @RequestParam @jakarta.validation.constraints.NotNull java.time.LocalDate attachedAt,
                       @RequestParam @jakarta.validation.constraints.NotNull PatientAffiliation.AffiliationStatus status) {
        var patient = users.findById(patientId).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Пациент не найден."));
        String center = medicalCenterName.trim();
        String doctor = doctorName.trim();
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
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Integer id) {
        affiliations.deleteById(id);
        return "redirect:/admin/patient-affiliations";
    }
}
