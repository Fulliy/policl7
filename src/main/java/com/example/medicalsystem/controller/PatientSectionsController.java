package com.example.medicalsystem.controller;

import com.example.medicalsystem.service.PatientSectionsService;
import com.example.medicalsystem.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@PreAuthorize("hasAuthority('PATIENT')")
public class PatientSectionsController {
    private final PatientSectionsService sectionsService;
    private final UserService userService;

    public PatientSectionsController(PatientSectionsService sectionsService, UserService userService) {
        this.sectionsService = sectionsService;
        this.userService = userService;
    }

    @GetMapping("/patient/referrals")
    public String referrals(@AuthenticationPrincipal UserDetails principal, Model model) {
        var patient = userService.findByUsername(principal.getUsername());
        model.addAttribute("referrals", sectionsService.referralsForPatient(patient.getIdUser()));
        return "patientReferrals";
    }

    @GetMapping("/patient/affiliations")
    public String affiliations(@AuthenticationPrincipal UserDetails principal, Model model) {
        var patient = userService.findByUsername(principal.getUsername());
        model.addAttribute("affiliations", sectionsService.affiliationsForPatient(patient.getIdUser()));
        return "patientAffiliations";
    }
}
