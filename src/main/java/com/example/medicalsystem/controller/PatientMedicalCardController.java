package com.example.medicalsystem.controller;

import com.example.medicalsystem.service.MedicalRecordService;
import com.example.medicalsystem.service.UserService;
import com.example.medicalsystem.service.AppointmentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/medical-card")
@PreAuthorize("hasAuthority('PATIENT')")
public class PatientMedicalCardController {
    private final MedicalRecordService medicalRecordService;
    private final UserService userService;
    private final AppointmentService appointmentService;

    @Autowired
    public PatientMedicalCardController(MedicalRecordService medicalRecordService, UserService userService,
                                        AppointmentService appointmentService) {
        this.medicalRecordService = medicalRecordService;
        this.userService = userService;
        this.appointmentService = appointmentService;
    }

    public PatientMedicalCardController(MedicalRecordService medicalRecordService, UserService userService) {
        this(medicalRecordService, userService, null);
    }

    @GetMapping
    public String show(@AuthenticationPrincipal UserDetails principal, Model model) {
        var patient = userService.findByUsername(principal.getUsername());
        model.addAttribute("medicalRecords", medicalRecordService.findAllForPatient(patient.getIdUser()));
        model.addAttribute("pastVisits", appointmentService == null ? java.util.List.of() : appointmentService.findByUser(patient.getIdUser()).stream()
                .filter(visit -> visit.getAppointmentDate() != null
                        && visit.getAppointmentDate().isBefore(java.time.LocalDateTime.now(java.time.ZoneId.of("Europe/Moscow")))
                        && "completed".equalsIgnoreCase(visit.getStatus()))
                .sorted(java.util.Comparator.comparing(com.example.medicalsystem.model.Appointment::getAppointmentDate).reversed())
                .toList());
        return "patientMedicalCard";
    }
}
