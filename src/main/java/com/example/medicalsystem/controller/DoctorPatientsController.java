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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/doctor/patients")
@PreAuthorize("hasAnyAuthority('DOCTOR', 'ROLE_DOCTOR')")
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
        var doctorVisits = appointments.findByDoctor(doctor.getIdUser());
        var now = java.time.LocalDateTime.now(java.time.ZoneId.of("Europe/Moscow"));
        var pastAppointmentIds = doctorVisits.stream()
                .filter(item -> item.getAppointmentDate() != null && !item.getAppointmentDate().isAfter(now))
                .filter(this::belongsToDoctorPatient).map(Appointment::getIdAppointment).toList();
        Map<Integer, java.util.List<com.example.medicalsystem.model.MedicalRecord>> recordsByAppointment = records.findByAppointments(pastAppointmentIds).stream()
                .filter(record -> record.getAppointment() != null)
                .collect(java.util.stream.Collectors.groupingBy(record -> record.getAppointment().getIdAppointment()));
        doctorVisits.stream()
                .filter(item -> item.getUser() != null)
                .filter(this::belongsToDoctorPatient)
                .sorted(Comparator.comparingLong(item -> item.getAppointmentDate() == null ? Long.MAX_VALUE
                        : Math.abs(java.time.Duration.between(now, item.getAppointmentDate()).toMinutes())))
                .forEach(item -> {
                    item.setMedicalRecords(recordsByAppointment.getOrDefault(item.getIdAppointment(), java.util.List.of()));
                    patients.putIfAbsent(item.getUser().getIdUser(), item);
                });
        model.addAttribute("patients", patients.values());
        model.addAttribute("now", now);
        return "doctorPatients";
    }

    @GetMapping("/{patientId}")
    public String patientCard(@PathVariable Integer patientId,
                              @RequestParam(required = false) Integer appointmentId,
                              @AuthenticationPrincipal UserDetails principal, Model model) {
        var doctor = users.findByUsername(principal.getUsername());
        var ownVisits = appointments.findByDoctor(doctor.getIdUser()).stream()
                .filter(item -> item.getUser() != null && patientId.equals(item.getUser().getIdUser()))
                .filter(this::belongsToDoctorPatient)
                .toList();
        if (ownVisits.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Пациент не найден среди ваших пациентов.");

        // Право доступа даёт хотя бы один состоявшийся визит к текущему врачу,
        // а сама карта показывает полную историю пациента у всех врачей.
        var patientVisits = appointments.findByUser(patientId);
        var visits = patientVisits.stream()
                .filter(visit -> belongsToPatientHistory(visit)
                        || (visit.getDoctor() != null && doctor.getIdUser().equals(visit.getDoctor().getIdUser())
                        && belongsToDoctorPatient(visit)))
                .sorted(Comparator.comparing(Appointment::getAppointmentDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        var recordAppointmentIds = java.util.stream.Stream.concat(patientVisits.stream(), ownVisits.stream())
                .map(Appointment::getIdAppointment).distinct().toList();
        var allRecords = records.findByAppointments(recordAppointmentIds);
        Map<Integer, java.util.List<com.example.medicalsystem.model.MedicalRecord>> recordsByAppointment = allRecords.stream()
                .filter(record -> record.getAppointment() != null)
                .collect(java.util.stream.Collectors.groupingBy(record -> record.getAppointment().getIdAppointment()));
        patientVisits.forEach(visit -> visit.setMedicalRecords(recordsByAppointment.getOrDefault(visit.getIdAppointment(), java.util.List.of())));
        visits.forEach(visit -> visit.setMedicalRecords(recordsByAppointment.getOrDefault(visit.getIdAppointment(), java.util.List.of())));
        ownVisits.forEach(visit -> visit.setMedicalRecords(recordsByAppointment.getOrDefault(visit.getIdAppointment(), java.util.List.of())));
        Appointment selectedVisit = appointmentId == null ? null : ownVisits.stream()
                .filter(visit -> appointmentId.equals(visit.getIdAppointment()))
                .filter(visit -> AppointmentStatus.parse(visit.getStatus()) == AppointmentStatus.pending
                        || AppointmentStatus.parse(visit.getStatus()) == AppointmentStatus.confirmed
                        || AppointmentStatus.parse(visit.getStatus()) == AppointmentStatus.completed)
                .findFirst().orElse(null);
        model.addAttribute("patient", ownVisits.get(0).getUser());
        model.addAttribute("now", java.time.LocalDateTime.now(java.time.ZoneId.of("Europe/Moscow")));
        model.addAttribute("currentDoctorId", doctor.getIdUser());
        model.addAttribute("visits", visits);
        model.addAttribute("allPatientVisits", patientVisits);
        model.addAttribute("selectedVisit", selectedVisit);
        model.addAttribute("medicalRecords", allRecords);
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
