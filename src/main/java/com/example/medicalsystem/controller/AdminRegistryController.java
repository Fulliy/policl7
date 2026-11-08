package com.example.medicalsystem.controller;

import com.example.medicalsystem.model.Appointment;
import com.example.medicalsystem.model.AppointmentStatus;
import com.example.medicalsystem.model.RoleEnum;
import com.example.medicalsystem.model.User;
import com.example.medicalsystem.repository.DoctorScheduleExceptionRepository;
import com.example.medicalsystem.repository.DoctorWorkingHoursRepository;
import com.example.medicalsystem.service.AppointmentService;
import com.example.medicalsystem.service.MedicalRecordService;
import com.example.medicalsystem.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/registry")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminRegistryController {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Europe/Moscow");

    private final UserService users;
    private final AppointmentService appointments;
    private final MedicalRecordService records;
    private final DoctorWorkingHoursRepository workingHours;
    private final DoctorScheduleExceptionRepository exceptions;

    public AdminRegistryController(UserService users, AppointmentService appointments,
                                   MedicalRecordService records,
                                   DoctorWorkingHoursRepository workingHours,
                                   DoctorScheduleExceptionRepository exceptions) {
        this.users = users;
        this.appointments = appointments;
        this.records = records;
        this.workingHours = workingHours;
        this.exceptions = exceptions;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public String registry(Model model) {
        List<User> all = users.findAll();
        List<User> patients = all.stream().filter(this::isPatient).toList();
        List<User> doctors = all.stream().filter(this::isDoctor).toList();
        Map<Integer, List<Appointment>> visitsByPatient = new LinkedHashMap<>();
        patients.forEach(patient -> visitsByPatient.put(patient.getIdUser(), patientAppointments(patient.getIdUser())));
        Map<Integer, List<Appointment>> visitsByDoctor = new LinkedHashMap<>();
        doctors.forEach(doctor -> visitsByDoctor.put(doctor.getIdUser(), doctorAppointments(doctor.getIdUser())));
        model.addAttribute("patients", patients.stream().map(patient -> new PatientRow(patient, visitsByPatient.get(patient.getIdUser()))).toList());
        model.addAttribute("doctors", doctors.stream().map(doctor -> new DoctorRow(doctor, visitsByDoctor.get(doctor.getIdUser()))).toList());
        model.addAttribute("patientCount", patients.size());
        model.addAttribute("doctorCount", doctors.size());
        model.addAttribute("appointmentCount", visitsByPatient.values().stream().mapToLong(List::size).sum());
        return "adminRegistry";
    }

    @GetMapping("/patients/{id}")
    @Transactional(readOnly = true)
    public String patient(@PathVariable Integer id, @RequestParam(defaultValue = "patients") String section, Model model) {
        User patient = users.findById(id);
        if (patient == null || !isPatient(patient)) throw notFound("Пациент не найден.");
        List<Appointment> visits = patientAppointments(id);
        model.addAttribute("patient", patient);
        model.addAttribute("section", "doctors".equals(section) ? "doctors" : "patients");
        model.addAttribute("visits", visits);
        model.addAttribute("medicalRecords", records.findAllForPatient(id));
        return "adminPatientCard";
    }

    @GetMapping("/doctors/{id}")
    @Transactional(readOnly = true)
    public String doctor(@PathVariable Integer id, @RequestParam(defaultValue = "doctors") String section, Model model) {
        User doctor = users.findById(id);
        if (doctor == null || !isDoctor(doctor)) throw notFound("Врач не найден.");
        List<Appointment> visits = doctorAppointments(id);
        Map<Integer, Appointment> patientRows = new LinkedHashMap<>();
        visits.stream().filter(item -> item.getUser() != null)
                .sorted(Comparator.comparing(Appointment::getAppointmentDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(item -> patientRows.putIfAbsent(item.getUser().getIdUser(), item));
        model.addAttribute("doctor", doctor);
        model.addAttribute("section", "patients".equals(section) ? "patients" : "doctors");
        model.addAttribute("visits", visits);
        model.addAttribute("patients", patientRows.values());
        model.addAttribute("workingHours", java.util.stream.IntStream.rangeClosed(1, 7)
                .boxed().flatMap(day -> workingHours.findByDoctor_IdUserAndDayOfWeek(id, day).stream()).toList());
        model.addAttribute("exceptions", exceptions.findByDoctor_IdUserAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                id, java.time.LocalDate.now(CLINIC_ZONE).plusYears(1), java.time.LocalDate.now(CLINIC_ZONE).minusYears(1)));
        return "adminDoctorCard";
    }

    private List<Appointment> patientAppointments(Integer patientId) {
        return appointments.findByUser(patientId).stream()
                .filter(item -> item.getAppointmentDate() != null)
                .sorted(Comparator.comparing(Appointment::getAppointmentDate, Comparator.reverseOrder()))
                .toList();
    }

    private List<Appointment> doctorAppointments(Integer doctorId) {
        return appointments.findByDoctor(doctorId).stream()
                .filter(item -> item.getAppointmentDate() != null)
                .sorted(Comparator.comparing(Appointment::getAppointmentDate, Comparator.reverseOrder()))
                .toList();
    }

    private boolean isPatient(User user) { return user.getRoles() != null && user.getRoles().contains(RoleEnum.PATIENT); }
    private boolean isDoctor(User user) { return user.getRoles() != null && user.getRoles().contains(RoleEnum.DOCTOR); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }

    public record PatientRow(User patient, List<Appointment> visits) {
        public Appointment lastVisit() { return visits.isEmpty() ? null : visits.get(0); }
        public long completedCount() { return visits.stream().filter(a -> status(a) == AppointmentStatus.completed).count(); }
        public long doctorCount() { return visits.stream().filter(a -> a.getDoctor() != null).map(a -> a.getDoctor().getIdUser()).distinct().count(); }
    }

    public record DoctorRow(User doctor, List<Appointment> visits) {
        public long patientCount() { return visits.stream().filter(a -> a.getUser() != null).map(a -> a.getUser().getIdUser()).distinct().count(); }
        public long completedCount() { return visits.stream().filter(a -> status(a) == AppointmentStatus.completed).count(); }
        public long upcomingCount() { return visits.stream().filter(a -> a.getAppointmentDate() != null && a.getAppointmentDate().isAfter(LocalDateTime.now(CLINIC_ZONE)) && (status(a) == AppointmentStatus.pending || status(a) == AppointmentStatus.confirmed)).count(); }
    }

    private static AppointmentStatus status(Appointment appointment) {
        try { return AppointmentStatus.parse(appointment.getStatus()); }
        catch (IllegalArgumentException ignored) { return AppointmentStatus.cancelled; }
    }
}
