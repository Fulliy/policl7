package com.example.medicalsystem.controller;

import com.example.medicalsystem.service.MedicalRecordService;
import com.example.medicalsystem.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/medical-card")
@PreAuthorize("hasAuthority('PATIENT')")
public class PatientMedicalCardController {
    private final MedicalRecordService medicalRecordService;
    private final UserService userService;

    public PatientMedicalCardController(MedicalRecordService medicalRecordService, UserService userService) {
        this.medicalRecordService = medicalRecordService;
        this.userService = userService;
    }

    @GetMapping
    public String show(@AuthenticationPrincipal UserDetails principal, Model model) {
        var patient = userService.findByUsername(principal.getUsername());
        model.addAttribute("medicalRecords", medicalRecordService.findAllForPatient(patient.getIdUser()));
        return "patientMedicalCard";
    }
}
