package com.example.medicalsystem.controller;

import com.example.medicalsystem.model.Appointment;
import com.example.medicalsystem.service.AppointmentService;
import com.example.medicalsystem.service.MedicalRecordService;
import com.example.medicalsystem.service.UserService;
import com.example.medicalsystem.model.AppointmentStatus;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/doctor/patients")
@PreAuthorize("hasAuthority('DOCTOR')")
public class DoctorPatientsController {
    private final AppointmentService appointments;
    private final MedicalRecordService records;
    private final UserService users;

    public DoctorPatientsController(AppointmentService appointments, MedicalRecordService records, UserService users) {
        this.appointments = appointments;
        this.records = records;
        this.users = users;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal UserDetails principal, Model model) {
        var doctor = users.findByUsername(principal.getUsername());
        Map<Integer, Appointment> patients = new LinkedHashMap<>();
        appointments.findByDoctor(doctor.getIdUser()).stream()
                .filter(item -> item.getUser() != null)
                .filter(this::belongsToDoctorPatient)
                .sorted(Comparator.comparing(Appointment::getAppointmentDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(item -> patients.putIfAbsent(item.getUser().getIdUser(), item));
        model.addAttribute("patients", patients.values());
        return "doctorPatients";
    }

    @GetMapping("/{patientId}")
    public String patientCard(@PathVariable Integer patientId,
                              @AuthenticationPrincipal UserDetails principal, Model model) {
        var doctor = users.findByUsername(principal.getUsername());
        var ownVisits = appointments.findByDoctor(doctor.getIdUser()).stream()
                .filter(item -> item.getUser() != null && patientId.equals(item.getUser().getIdUser()))
                .filter(this::belongsToPatientHistory)
                .toList();
        if (ownVisits.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Пациент не найден среди ваших пациентов.");

        // Право доступа даёт хотя бы один состоявшийся визит к текущему врачу,
        // а сама карта показывает полную историю пациента у всех врачей.
        var visits = appointments.findByUser(patientId).stream()
                .filter(this::belongsToPatientHistory)
                .sorted(Comparator.comparing(Appointment::getAppointmentDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        model.addAttribute("patient", ownVisits.get(0).getUser());
        model.addAttribute("currentDoctorId", doctor.getIdUser());
        model.addAttribute("visits", visits);
        model.addAttribute("medicalRecords", records.findAllForPatient(patientId));
        return "doctorPatientCard";
    }

    private boolean belongsToDoctorPatient(Appointment appointment) {
        try {
            AppointmentStatus status = AppointmentStatus.parse(appointment.getStatus());
            return status != AppointmentStatus.cancelled && status != AppointmentStatus.no_show;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean belongsToPatientHistory(Appointment appointment) {
        try {
            return AppointmentStatus.parse(appointment.getStatus()) == AppointmentStatus.completed
                    || (appointment.getAppointmentDate() != null
                    && appointment.getAppointmentDate().isBefore(java.time.LocalDateTime.now(java.time.ZoneId.of("Europe/Moscow")))
                    && AppointmentStatus.parse(appointment.getStatus()) != AppointmentStatus.cancelled);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
