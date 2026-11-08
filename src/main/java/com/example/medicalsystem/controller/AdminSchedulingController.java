package com.example.medicalsystem.controller;

import com.example.medicalsystem.model.Appointment;
import com.example.medicalsystem.model.AppointmentSlotHold;
import com.example.medicalsystem.model.AppointmentStatus;
import com.example.medicalsystem.model.DoctorScheduleException;
import com.example.medicalsystem.model.PatientBookingRestriction;
import com.example.medicalsystem.repository.AppointmentRepository;
import com.example.medicalsystem.repository.DoctorScheduleExceptionRepository;
import com.example.medicalsystem.repository.PatientBookingRestrictionRepository;
import com.example.medicalsystem.repository.UserRepository;
import com.example.medicalsystem.service.AppointmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/admin/scheduling")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminSchedulingController {
    private final AppointmentService appointments;
    private final AppointmentRepository appointmentRepository;
    private final DoctorScheduleExceptionRepository exceptionRepository;
    private final PatientBookingRestrictionRepository restrictionRepository;
    private final UserRepository userRepository;

    public AdminSchedulingController(AppointmentService appointments,
                                     AppointmentRepository appointmentRepository,
                                     DoctorScheduleExceptionRepository exceptionRepository,
                                     PatientBookingRestrictionRepository restrictionRepository,
                                     UserRepository userRepository) {
        this.appointments = appointments;
        this.appointmentRepository = appointmentRepository;
        this.exceptionRepository = exceptionRepository;
        this.restrictionRepository = restrictionRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/appointments-requiring-reschedule")
    @Transactional(readOnly = true)
    public List<AppointmentNeedingReschedule> requiringReschedule() {
        return appointmentRepository.findByRequiresRescheduleTrue().stream()
                .filter(item -> {
                    try { return AppointmentStatus.parse(item.getStatus()).occupiesSlot(); }
                    catch (IllegalArgumentException ignored) { return false; }
                })
                .map(item -> new AppointmentNeedingReschedule(item.getIdAppointment(),
                        item.getUser() == null ? null : item.getUser().getDisplayName(),
                        item.getDoctor() == null ? null : item.getDoctor().getDisplayName(),
                        item.getAppointmentDate(), item.getComplaint()))
                .toList();
    }

    @PostMapping("/exceptions")
    @Transactional
    public ExceptionCreated closeDoctorDates(@Valid @RequestBody ScheduleClosureRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Дата окончания не может быть раньше даты начала.");
        }
        var doctor = userRepository.findById(request.doctorId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Врач не найден."));
        boolean isDoctor = doctor.getRoles() != null && doctor.getRoles().stream()
                .anyMatch(role -> "DOCTOR".equals(role.name()));
        if (!isDoctor) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выбранный пользователь не является врачом.");
        appointments.lockDoctorScheduleChange(doctor.getIdUser());

        DoctorScheduleException exception = new DoctorScheduleException();
        exception.setDoctor(doctor);
        exception.setStartDate(request.startDate());
        exception.setEndDate(request.endDate());
        exception.setType(request.type());
        exception.setDescription(request.description());
        DoctorScheduleException saved = exceptionRepository.saveAndFlush(exception);

        List<Appointment> affected = appointmentRepository
                .findByDoctor_IdUserAndAppointmentDateBetweenAndStatusIn(doctor.getIdUser(),
                        request.startDate().atStartOfDay(), request.endDate().plusDays(1).atStartOfDay().minusNanos(1),
                        List.of(AppointmentStatus.pending.name(), AppointmentStatus.confirmed.name()));
        affected.forEach(item -> item.setRequiresReschedule(true));
        appointmentRepository.saveAll(affected);
        return new ExceptionCreated(saved.getId(), affected.size());
    }

    @PutMapping("/exceptions/{exceptionId}")
    @Transactional
    public ExceptionCreated updateDoctorDates(@PathVariable Integer exceptionId,
                                               @Valid @RequestBody ScheduleClosureRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Дата окончания не может быть раньше даты начала.");
        }
        DoctorScheduleException exception = exceptionRepository.findById(exceptionId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Исключение расписания не найдено."));
        Integer oldDoctorId = exception.getDoctor().getIdUser();
        var doctor = userRepository.findById(request.doctorId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Врач не найден."));
        boolean isDoctor = doctor.getRoles() != null && doctor.getRoles().stream().anyMatch(role -> "DOCTOR".equals(role.name()));
        if (!isDoctor) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выбранный пользователь не является врачом.");
        if (oldDoctorId.equals(doctor.getIdUser())) {
            appointments.lockDoctorScheduleChange(oldDoctorId);
        } else {
            appointments.lockDoctorScheduleChange(Math.min(oldDoctorId, doctor.getIdUser()));
            appointments.lockDoctorScheduleChange(Math.max(oldDoctorId, doctor.getIdUser()));
        }

        LocalDate oldStart = exception.getStartDate();
        LocalDate oldEnd = exception.getEndDate();
        exception.setDoctor(doctor);
        exception.setStartDate(request.startDate());
        exception.setEndDate(request.endDate());
        exception.setType(request.type());
        exception.setDescription(request.description());
        exceptionRepository.saveAndFlush(exception);

        List<Appointment> oldAffected = appointmentRepository.findByDoctor_IdUserAndAppointmentDateBetweenAndStatusIn(
                oldDoctorId, oldStart.atStartOfDay(), oldEnd.plusDays(1).atStartOfDay().minusNanos(1),
                List.of(AppointmentStatus.pending.name(), AppointmentStatus.confirmed.name()));
        oldAffected.stream().filter(Appointment::isRequiresReschedule)
                .filter(item -> exceptionRepository.findByDoctor_IdUserAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        oldDoctorId, item.getAppointmentDate().toLocalDate(), item.getAppointmentDate().toLocalDate()).isEmpty())
                .forEach(item -> item.setRequiresReschedule(false));

        List<Appointment> affected = appointmentRepository.findByDoctor_IdUserAndAppointmentDateBetweenAndStatusIn(
                doctor.getIdUser(), request.startDate().atStartOfDay(), request.endDate().plusDays(1).atStartOfDay().minusNanos(1),
                List.of(AppointmentStatus.pending.name(), AppointmentStatus.confirmed.name()));
        affected.forEach(item -> item.setRequiresReschedule(true));
        appointmentRepository.saveAll(oldAffected);
        appointmentRepository.saveAll(affected);
        return new ExceptionCreated(exceptionId, affected.size());
    }

    @DeleteMapping("/exceptions/{exceptionId}")
    @Transactional
    public void reopenDoctorDates(@PathVariable Integer exceptionId) {
        DoctorScheduleException exception = exceptionRepository.findById(exceptionId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Исключение расписания не найдено."));
        Integer doctorId = exception.getDoctor().getIdUser();
        LocalDate start = exception.getStartDate();
        LocalDate end = exception.getEndDate();
        appointments.lockDoctorScheduleChange(doctorId);
        exceptionRepository.delete(exception);
        List<Appointment> marked = appointmentRepository.findByDoctor_IdUserAndAppointmentDateBetweenAndStatusIn(
                doctorId, start.atStartOfDay(), end.plusDays(1).atStartOfDay().minusNanos(1),
                List.of(AppointmentStatus.pending.name(), AppointmentStatus.confirmed.name()));
        marked.stream().filter(Appointment::isRequiresReschedule)
                .filter(item -> exceptionRepository
                        .findByDoctor_IdUserAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                                doctorId, item.getAppointmentDate().toLocalDate(), item.getAppointmentDate().toLocalDate()).isEmpty())
                .forEach(item -> item.setRequiresReschedule(false));
        appointmentRepository.saveAll(marked);
    }

    @PostMapping("/appointments/{appointmentId}/reschedule")
    @Transactional
    public Appointment reschedule(@PathVariable Integer appointmentId, @Valid @RequestBody RescheduleRequest request) {
        Appointment appointment = appointmentRepository.findById(appointmentId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Запись не найдена."));
        appointment.setAppointmentDate(request.appointmentDate());
        return appointments.saveAdminRescheduled(appointment);
    }

    @PostMapping("/patients/{patientId}/clear-no-show-block")
    @Transactional
    public void clearNoShowBlock(@PathVariable Integer patientId) {
        if (!userRepository.existsById(patientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Пациент не найден.");
        }
        PatientBookingRestriction restriction = restrictionRepository.findById(patientId)
                .orElseGet(() -> {
                    PatientBookingRestriction created = new PatientBookingRestriction();
                    created.setUserId(patientId);
                    return created;
                });
        restriction.setNoShowBlockOverridden(true);
        restriction.setUpdatedAt(LocalDateTime.now());
        restrictionRepository.save(restriction);
    }

    public record ScheduleClosureRequest(@NotNull Integer doctorId, @NotNull LocalDate startDate,
                                         @NotNull LocalDate endDate, @NotNull DoctorScheduleException.ExceptionType type,
                                         String description) { }
    public record RescheduleRequest(@NotNull LocalDateTime appointmentDate) { }
    public record ExceptionCreated(Integer exceptionId, int affectedAppointments) { }
    public record AppointmentNeedingReschedule(Integer id, String patient, String doctor,
                                                LocalDateTime appointmentDate, String complaint) { }
}
