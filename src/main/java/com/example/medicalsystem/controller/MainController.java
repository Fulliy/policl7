package com.example.medicalsystem.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.medicalsystem.service.AppointmentService;
import com.example.medicalsystem.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Comparator;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Controller
@Tag(name = "Главная", description = "Главная страница приложения")
public class MainController {

    private final AppointmentService appointmentService;
    private final UserService userService;

    public MainController(AppointmentService appointmentService, UserService userService) {
        this.appointmentService = appointmentService;
        this.userService = userService;
    }

    @GetMapping("/")
    public String index(Model model, @AuthenticationPrincipal UserDetails principal) {
        boolean admin = principal.getAuthorities().stream().anyMatch(a -> "ADMIN".equals(a.getAuthority()));
        boolean doctorOnly = principal.getAuthorities().stream().anyMatch(a -> "DOCTOR".equals(a.getAuthority()))
                && !admin;
        if (admin) {
            return "redirect:/appointments";
        }
        if (doctorOnly) {
            var doctor = userService.findByUsername(principal.getUsername());
            LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
            LocalDateTime startOfTomorrow = startOfToday.plusDays(1);
            LocalDateTime startOfNextWeek = startOfToday.plusDays(7);
            var weekAppointments = appointmentService.findByDoctor(doctor.getIdUser()).stream()
                    .filter(appointment -> appointment.getAppointmentDate() != null
                            && !appointment.getAppointmentDate().isBefore(startOfToday)
                            && appointment.getAppointmentDate().isBefore(startOfNextWeek))
                    .sorted(Comparator.comparing(com.example.medicalsystem.model.Appointment::getAppointmentDate))
                    .toList();
            model.addAttribute("doctor", doctor);
            model.addAttribute("today", LocalDate.now());
            model.addAttribute("todayAppointments", weekAppointments.stream()
                    .filter(appointment -> appointment.getAppointmentDate().isBefore(startOfTomorrow)).toList());
            model.addAttribute("weekAppointments", weekAppointments);
            model.addAttribute("appointmentsToday", weekAppointments.stream()
                    .filter(appointment -> appointment.getAppointmentDate().isBefore(startOfTomorrow)).count());
            return "doctor_dashboard";
        }
        boolean patientRole = principal.getAuthorities().stream().anyMatch(a -> "PATIENT".equals(a.getAuthority()));
        if (patientRole) {
            var patient = userService.findByUsername(principal.getUsername());
            model.addAttribute("patient", patient);
            model.addAttribute("patientAppointments",
                    appointmentService.findByUser(patient.getIdUser()).stream()
                            .sorted(Comparator.comparing(com.example.medicalsystem.model.Appointment::getAppointmentDate,
                                    Comparator.nullsLast(Comparator.naturalOrder())))
                            .toList());
            return "patient_dashboard";
        }
        model.addAttribute("patientOnly", false);
        return "index";
    }
}
