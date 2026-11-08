package com.example.medicalsystem.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.medicalsystem.service.AppointmentService;
import com.example.medicalsystem.service.UserService;
import com.example.medicalsystem.model.Appointment;
import com.example.medicalsystem.model.RoleEnum;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Comparator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;

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
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Moscow"));
            LocalDate today = now.toLocalDate();
            LocalDateTime startOfToday = today.atStartOfDay();
            LocalDateTime startOfTomorrow = startOfToday.plusDays(1);
            var users = userService.findAll();
            var upcomingAppointments = appointmentService.findUpcomingScheduled(now.toLocalDateTime()).stream()
                    .filter(appointment -> appointment.getAppointmentDate() != null
                            && appointment.getAppointmentDate().isAfter(now.toLocalDateTime()))
                    .toList();
            var todaysAppointments = appointmentService.findByDateRange(startOfToday, startOfTomorrow).stream()
                    .filter(appointment -> appointment.getAppointmentDate() != null
                            && !appointment.getAppointmentDate().isBefore(startOfToday)
                            && appointment.getAppointmentDate().isBefore(startOfTomorrow))
                    .filter(appointment -> "pending".equalsIgnoreCase(appointment.getStatus())
                            || "confirmed".equalsIgnoreCase(appointment.getStatus()))
                    .toList();
            var departmentStats = upcomingAppointments.stream()
                    .collect(Collectors.groupingBy(appointment -> appointment.getDepartment() == null
                                    ? "Отделение не указано" : appointment.getDepartment().getDepartmentName(),
                            Collectors.counting()))
                    .entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(5)
                    .map(entry -> Map.of("name", entry.getKey(), "count", entry.getValue()))
                    .toList();
            long maxDepartmentCount = departmentStats.stream()
                    .mapToLong(stat -> (Long) stat.get("count"))
                    .max().orElse(0);

            model.addAttribute("adminName", principal.getUsername());
            model.addAttribute("welcomeMessage", now.getHour() < 12 ? "Доброе утро" : now.getHour() < 18 ? "Добрый день" : "Добрый вечер");
            model.addAttribute("today", today);
            model.addAttribute("appointmentsToday", todaysAppointments.size());
            model.addAttribute("todayAppointments", todaysAppointments.stream()
                    .filter(appointment -> "pending".equalsIgnoreCase(appointment.getStatus())
                            || "confirmed".equalsIgnoreCase(appointment.getStatus()))
                    .sorted(Comparator.comparing(Appointment::getAppointmentDate))
                    .toList());
            model.addAttribute("patientCount", users.stream()
                    .filter(user -> user.getRoles() != null && user.getRoles().contains(RoleEnum.PATIENT)).count());
            model.addAttribute("doctorCount", users.stream()
                    .filter(user -> user.getRoles() != null && user.getRoles().contains(RoleEnum.DOCTOR)).count());
            model.addAttribute("upcomingAppointments", upcomingAppointments.stream().limit(5).toList());
            model.addAttribute("nextAppointment", upcomingAppointments.isEmpty() ? null : upcomingAppointments.get(0));
            model.addAttribute("departmentStats", departmentStats);
            model.addAttribute("maxDepartmentCount", maxDepartmentCount);
            return "admin_dashboard";
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
                    .filter(appointment -> "pending".equalsIgnoreCase(appointment.getStatus())
                            || "confirmed".equalsIgnoreCase(appointment.getStatus()))
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
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Europe/Moscow"));
            var patientAppointments = appointmentService.findByUser(patient.getIdUser()).stream()
                    .filter(appointment -> appointment.getAppointmentDate() != null)
                    .filter(appointment -> !"cancelled".equalsIgnoreCase(appointment.getStatus()))
                    .sorted(Comparator.comparing(Appointment::getAppointmentDate,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            var nextAppointment = patientAppointments.stream()
                    .filter(appointment -> appointment.getAppointmentDate() != null
                            && !appointment.getAppointmentDate().isBefore(now)
                            && ("pending".equalsIgnoreCase(appointment.getStatus())
                            || "confirmed".equalsIgnoreCase(appointment.getStatus())))
                    .findFirst().orElse(null);
            var allUsers = userService.findAll();
            var doctors = allUsers.stream()
                    .filter(user -> user.getRoles() != null && user.getRoles().contains(RoleEnum.DOCTOR))
                    .limit(4)
                    .map(user -> new DoctorCard(user.getIdUser(), user.getDisplayName(),
                            user.getSpecialty() == null ? "Врач" : user.getSpecialty().getSpecialtyName(),
                            initials(user.getFirstName(), user.getSurname()), true))
                    .toList();
            if (doctors.isEmpty()) {
                doctors = List.of(
                        new DoctorCard(null, "Врач-терапевт", "Терапия", "ВТ", false),
                        new DoctorCard(null, "Врач-кардиолог", "Кардиология", "ВК", false),
                        new DoctorCard(null, "Врач-невролог", "Неврология", "ВН", false),
                        new DoctorCard(null, "Врач-педиатр", "Педиатрия", "ВП", false));
            }
            var specialtyDoctors = allUsers.stream()
                    .filter(user -> user.getRoles() != null && user.getRoles().contains(RoleEnum.DOCTOR))
                    .filter(user -> user.getSpecialty() != null)
                    .collect(Collectors.groupingBy(user -> user.getSpecialty().getSpecialtyName(),
                            LinkedHashMap::new, Collectors.toList()));
            List<SpecialtyCard> specialties = new ArrayList<>(List.of(
                    new SpecialtyCard(null, "Терапия", "＋", 0),
                    new SpecialtyCard(null, "Кардиология", "♡", 0),
                    new SpecialtyCard(null, "Травматология", "✚", 0),
                    new SpecialtyCard(null, "Педиатрия", "⌂", 0),
                    new SpecialtyCard(null, "Неврология", "✦", 0)));
            specialtyDoctors.forEach((name, assignedDoctors) -> {
                SpecialtyCard card = new SpecialtyCard(assignedDoctors.get(0).getSpecialty().getIdSpecialty(),
                        name, "✚", assignedDoctors.size());
                int existingIndex = java.util.stream.IntStream.range(0, specialties.size())
                        .filter(index -> specialties.get(index).name().equalsIgnoreCase(name))
                        .findFirst().orElse(-1);
                if (existingIndex >= 0) specialties.set(existingIndex, card);
                else specialties.add(card);
            });
            model.addAttribute("patient", patient);
            model.addAttribute("patientAppointments", patientAppointments);
            model.addAttribute("now", now);
            model.addAttribute("nextAppointment", nextAppointment);
            model.addAttribute("patientDoctors", doctors);
            model.addAttribute("patientSpecialties", specialties);
            return "patient_dashboard";
        }
        model.addAttribute("patientOnly", false);
        return "index";
    }

    private static String initials(String firstName, String surname) {
        String first = firstName == null || firstName.isBlank() ? "" : firstName.substring(0, 1);
        String last = surname == null || surname.isBlank() ? "" : surname.substring(0, 1);
        return (first + last).toUpperCase(java.util.Locale.forLanguageTag("ru"));
    }

    public record DoctorCard(Integer id, String fullName, String specialty, String initials, boolean fromDatabase) { }
    public record SpecialtyCard(Integer id, String name, String icon, int doctorCount) { }
}
