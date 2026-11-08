package com.example.medicalsystem.service;

import com.example.medicalsystem.model.Appointment;
import com.example.medicalsystem.model.AppointmentSlotHold;
import com.example.medicalsystem.model.AppointmentType;
import com.example.medicalsystem.model.AppointmentStatus;
import com.example.medicalsystem.model.DoctorScheduleBreak;
import com.example.medicalsystem.model.DoctorScheduleException;
import com.example.medicalsystem.model.DoctorWorkingHours;
import com.example.medicalsystem.model.PatientBookingRestriction;
import com.example.medicalsystem.model.User;
import com.example.medicalsystem.model.RoleEnum;
import com.example.medicalsystem.config.SchedulingProperties;
import com.example.medicalsystem.repository.*;
import jakarta.annotation.PostConstruct;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.temporal.WeekFields;

@Service
@Transactional
public class AppointmentService {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Europe/Moscow");

    private final AppointmentRepository repository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final DoctorWorkingHoursRepository workingHoursRepository;
    private final DoctorScheduleBreakRepository breakRepository;
    private final DoctorScheduleExceptionRepository exceptionRepository;
    private final PatientBookingRestrictionRepository restrictionRepository;
    private final AppointmentSlotHoldRepository holdRepository;
    private final SchedulingProperties scheduling;
    private final JdbcTemplate jdbc;

    @Value("${app.demo-data.enabled:false}")
    private boolean demoDataEnabled;

    @Autowired
    public AppointmentService(AppointmentRepository repository, UserRepository userRepository,
                              DepartmentRepository departmentRepository,
                              DoctorWorkingHoursRepository workingHoursRepository,
                              DoctorScheduleBreakRepository breakRepository,
                              DoctorScheduleExceptionRepository exceptionRepository,
                              PatientBookingRestrictionRepository restrictionRepository,
                              SchedulingProperties scheduling, JdbcTemplate jdbc,
                              AppointmentSlotHoldRepository holdRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.workingHoursRepository = workingHoursRepository;
        this.breakRepository = breakRepository;
        this.exceptionRepository = exceptionRepository;
        this.restrictionRepository = restrictionRepository;
        this.holdRepository = holdRepository;
        this.scheduling = scheduling;
        this.jdbc = jdbc;
    }

    public AppointmentService(AppointmentRepository repository, UserRepository userRepository,
                              DepartmentRepository departmentRepository,
                              DoctorWorkingHoursRepository workingHoursRepository,
                              DoctorScheduleBreakRepository breakRepository,
                              DoctorScheduleExceptionRepository exceptionRepository,
                              PatientBookingRestrictionRepository restrictionRepository,
                              SchedulingProperties scheduling, JdbcTemplate jdbc) {
        this(repository, userRepository, departmentRepository, workingHoursRepository, breakRepository,
                exceptionRepository, restrictionRepository, scheduling, jdbc, null);
    }

    public AppointmentService(AppointmentRepository repository, UserRepository userRepository,
                              DepartmentRepository departmentRepository) {
        this(repository, userRepository, departmentRepository, null, null, null, null,
                new SchedulingProperties(), null);
    }

    @PostConstruct
    public void init() {
        if (!demoDataEnabled) {
            return;
        }
        long count = repository.count();
        for (int i = (int) count + 1; i <= 10; i++) {
            Appointment appointment = new Appointment();
            appointment.setAppointmentDate(LocalDateTime.now().plusDays(i));
            appointment.setStatus("pending");
            appointment.setComplaint("Жалоба " + i);
            appointment.setUser(userRepository.findById(1).orElse(null));
            appointment.setDepartment(departmentRepository.findById(1).orElse(null));
            repository.save(appointment);
        }
    }

    public List<Appointment> findAll() {
        return repository.findAll();
    }

    public Page<Appointment> findPage(int page) {
        return repository.findAll(PaginationHelper.pageable(page));
    }

    public Appointment findById(Integer id) {
        return repository.findById(id).orElse(null);
    }

    public Appointment save(Appointment appointment) {
        return repository.save(appointment);
    }

    public Appointment saveBooking(Appointment appointment) {
        return saveBooking(appointment, null, false, false);
    }

    public Appointment saveBooking(Appointment appointment, Integer holdId, boolean administrator,
                                   boolean administratorCreated) {
        AppointmentStatus next = parseStatus(appointment.getStatus());
        Appointment existing = appointment.getIdAppointment() == null ? null : repository.findById(appointment.getIdAppointment()).orElse(null);
        validateStatusTransition(existing, next, administrator);
        boolean sameAssignedDoctor = appointment.getDoctor() != null && existing != null && existing.getDoctor() != null
                && appointment.getDoctor().getIdUser().equals(existing.getDoctor().getIdUser());
        if (existing != null && existing.getAppointmentDate() != null
                && existing.getAppointmentDate().isBefore(now()) && !administrator && !sameAssignedDoctor) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Нельзя изменить прошедший приём.");
        }
        appointment.setStatus(next.name());
        if (!administrator) validateRescheduleDeadline(existing, appointment, next);
        if (!administrator) validateAppointmentWindow(appointment.getAppointmentDate(), next.occupiesSlot());
        int duration = appointmentDuration(appointment);
        appointment.setDurationMinutes(duration);
        if (!next.occupiesSlot()) return repository.saveAndFlush(appointment);
        if (appointment.getUser() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пациент обязателен.");

        lockBookingKeys(appointment);
        AppointmentSlotHold hold = null;
        if (holdId != null) {
            cleanupExpiredHolds();
            hold = holdRepository == null ? null : holdRepository
                    .findByIdAndPatient_IdUser(holdId, appointment.getUser().getIdUser()).orElse(null);
            if (hold == null || !hold.getExpiresAt().isAfter(now())) throw holdExpired();
            if (!sameUserId(hold.getDoctor(), appointment.getDoctor())
                    || !java.util.Objects.equals(hold.getAppointmentDate(), appointment.getAppointmentDate())
                    || hold.getDurationMinutes() != duration) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Резерв не соответствует выбранному времени.");
            }
        }
        if (!administrator) {
            validatePatientRestrictions(appointment.getUser().getIdUser());
            validatePatientLimits(appointment, existing, duration);
        }
        boolean doctorVisit = appointment.getType() == null || appointment.getType() == AppointmentType.DOCTOR_VISIT;
        if (doctorVisit) {
            if (appointment.getDoctor() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выберите врача.");
            LocalDateTime start = appointment.getAppointmentDate();
            LocalDateTime end = start.plusMinutes(duration);
            validateDoctorSchedule(appointment.getDoctor().getIdUser(), start, end);
            if (hasDoctorOverlap(appointment.getDoctor().getIdUser(), start, end, appointment.getIdAppointment())) throw slotOccupied();
            if (hasActiveHoldOverlap(appointment.getDoctor().getIdUser(), start, end,
                    hold == null ? null : hold.getId(), null)) throw slotOccupied();
        }
        if (hasPatientOverlap(appointment.getUser().getIdUser(), appointment.getAppointmentDate(),
                appointment.getAppointmentDate().plusMinutes(duration), appointment.getIdAppointment())) throw slotOccupied();
        try {
            if (existing != null) {
                appointment.setRequiresReschedule(existing.isRequiresReschedule());
                appointment.setCreatedByAdmin(existing.isCreatedByAdmin() || administratorCreated);
            } else {
                appointment.setRequiresReschedule(false);
                appointment.setCreatedByAdmin(administratorCreated);
            }
            Appointment saved = repository.saveAndFlush(appointment);
            if (hold != null) holdRepository.delete(hold);
            return saved;
        } catch (DataIntegrityViolationException exception) {
            throw slotOccupied();
        }
    }

    public HoldResult reserveSlot(Integer patientId, Integer doctorId, LocalDateTime start, int durationMinutes) {
        if (holdRepository == null) throw new IllegalStateException("Резервирование слотов не настроено.");
        if (patientId == null || doctorId == null || start == null || durationMinutes <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выберите врача, дату и время приёма.");
        }
        Appointment lockKey = new Appointment();
        User patient = userRepository.findById(patientId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Пациент не найден."));
        User doctor = userRepository.findById(doctorId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Врач не найден."));
        if (doctor.getRoles() == null || !doctor.getRoles().contains(RoleEnum.DOCTOR)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выбранный пользователь не является врачом.");
        }
        lockKey.setUser(patient);
        lockKey.setDoctor(doctor);
        lockBookingKeys(lockKey);
        cleanupExpiredHolds();

        validateAppointmentWindow(start, true);
        LocalDateTime end = start.plusMinutes(durationMinutes);
        validateDoctorSchedule(doctorId, start, end);
        if (hasDoctorOverlap(doctorId, start, end, null)
                || hasActiveHoldOverlap(doctorId, start, end, null, patientId)) throw slotOccupied();
        validatePatientRestrictions(patientId);
        Appointment candidate = new Appointment();
        candidate.setUser(patient);
        candidate.setDoctor(doctor);
        candidate.setAppointmentDate(start);
        candidate.setStatus(AppointmentStatus.pending.name());
        candidate.setDurationMinutes(durationMinutes);
        validatePatientLimits(candidate, null, durationMinutes);

        holdRepository.deleteByPatient_IdUser(patientId);
        AppointmentSlotHold hold = new AppointmentSlotHold();
        hold.setPatient(patient);
        hold.setDoctor(doctor);
        hold.setAppointmentDate(start);
        hold.setDurationMinutes(durationMinutes);
        hold.setExpiresAt(now().plusMinutes(scheduling.getHoldDurationMinutes()));
        try {
            AppointmentSlotHold saved = holdRepository.saveAndFlush(hold);
            return new HoldResult(saved.getId(), saved.getExpiresAt());
        } catch (DataIntegrityViolationException exception) {
            throw slotOccupied();
        }
    }

    public HoldResult reserveSlot(Integer patientId, Integer doctorId, LocalDateTime start) {
        return reserveSlot(patientId, doctorId, start, scheduling.getDefaultVisitDurationMinutes());
    }

    public Appointment saveAdminRescheduled(Appointment appointment) {
        if (appointment.getStatus() == null
                || !parseStatus(appointment.getStatus()).occupiesSlot()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Перенести можно только активную запись.");
        }
        appointment.setRequiresReschedule(false);
        return saveBooking(appointment, null, true, false);
    }

    public void cleanupExpiredHolds() {
        if (holdRepository != null) holdRepository.deleteExpired(now());
    }

    public void lockDoctorScheduleChange(Integer doctorId) {
        if (jdbc != null) jdbc.execute("select pg_advisory_xact_lock(" + doctorId + ", 1)");
    }

    public record HoldResult(Integer holdId, LocalDateTime expiresAt) { }

    public SlotAvailability availability(Integer doctorId, LocalDate date) {
        return availability(doctorId, date, scheduling.getDefaultVisitDurationMinutes());
    }

    public SlotAvailability availability(Integer doctorId, LocalDate date, int durationMinutes) {
        return availability(doctorId, date, durationMinutes, true);
    }

    public SlotAvailability availabilityWithoutNearestDate(Integer doctorId, LocalDate date) {
        return availability(doctorId, date, scheduling.getDefaultVisitDurationMinutes(), false);
    }

    private SlotAvailability availability(Integer doctorId, LocalDate date, int durationMinutes, boolean findNearest) {
        cleanupExpiredHolds();
        List<SlotDto> values = new ArrayList<>();
        for (DoctorWorkingHours shift : shiftsFor(doctorId, date)) {
            List<DoctorScheduleBreak> breaks = breakRepository.findByWorkingHours_Id(shift.getId());
            for (LocalTime time = shift.getStartTime(); !time.plusMinutes(durationMinutes).isAfter(shift.getEndTime());
                 time = time.plusMinutes(durationMinutes)) {
                LocalTime slotStart = time;
                LocalTime slotEnd = time.plusMinutes(durationMinutes);
                LocalDateTime start = date.atTime(slotStart), end = date.atTime(slotEnd);
                boolean blockedByBreak = breaks.stream().anyMatch(b -> overlaps(slotStart, slotEnd, b.getStartTime(), b.getEndTime()));
                boolean available = withinBookingWindow(start) && !blockedByBreak
                        && !hasDoctorOverlap(doctorId, start, end, null)
                        && !hasActiveHoldOverlap(doctorId, start, end, null, null);
                values.add(new SlotDto(time.toString(), available));
            }
        }
        values.sort(java.util.Comparator.comparing(SlotDto::time));
        boolean available = values.stream().anyMatch(SlotDto::available);
        return new SlotAvailability(date, available, values,
                available || !findNearest ? null : nearestAvailableDate(doctorId, date, durationMinutes));
    }

    public Map<LocalDate, Boolean> monthAvailability(Integer doctorId, LocalDate month) {
        LocalDate first = month.withDayOfMonth(1);
        LocalDate last = first.plusMonths(1);
        return first.datesUntil(last).collect(Collectors.toMap(date -> date,
                date -> availability(doctorId, date, scheduling.getDefaultVisitDurationMinutes(), false).available()));
    }

    private LocalDate nearestAvailableDate(Integer doctorId, LocalDate from, int durationMinutes) {
        for (int offset = 1; offset <= scheduling.getNearestDateSearchDays(); offset++) {
            LocalDate candidate = from.plusDays(offset);
            if (availability(doctorId, candidate, durationMinutes, false).available()) return candidate;
        }
        return null;
    }

    private static boolean isActive(Appointment appointment) {
        return parseStatus(appointment.getStatus()).occupiesSlot();
    }

    private void validateAppointmentWindow(LocalDateTime start, boolean active) {
        if (start == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Дата и время обязательны.");
        LocalDateTime now = now();
        if (active && start.isBefore(now)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Слоты в прошлом недоступны.");
        if (active && start.isBefore(now.plusMinutes(scheduling.getMinNoticeMinutes()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Запись возможна не менее чем за " + scheduling.getMinNoticeMinutes() + " минут до приёма.");
        }
        if (active && start.isAfter(now.plusDays(scheduling.getMaxAdvanceDays()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Запись доступна не далее чем на " + scheduling.getMaxAdvanceDays() + " дней вперёд.");
        }
    }

    private void validateRescheduleDeadline(Appointment existing, Appointment updated, AppointmentStatus next) {
        if (existing == null || !next.occupiesSlot() || !isActive(existing)) return;
        boolean changed = !java.util.Objects.equals(existing.getAppointmentDate(), updated.getAppointmentDate())
                || !sameUserId(existing.getDoctor(), updated.getDoctor())
                || existing.getType() != updated.getType();
        if (changed && existing.getAppointmentDate() != null
                && existing.getAppointmentDate().minusHours(scheduling.getCancellationDeadlineHours()).isBefore(now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Отмена возможна только по телефону регистратуры");
        }
    }

    private static boolean sameUserId(com.example.medicalsystem.model.User left,
                                      com.example.medicalsystem.model.User right) {
        return java.util.Objects.equals(left == null ? null : left.getIdUser(), right == null ? null : right.getIdUser());
    }

    private boolean withinBookingWindow(LocalDateTime start) {
        LocalDateTime now = now();
        return !start.isBefore(now.plusMinutes(scheduling.getMinNoticeMinutes()))
                && !start.isAfter(now.plusDays(scheduling.getMaxAdvanceDays()));
    }

    private int appointmentDuration(Appointment appointment) {
        if (appointment.getServices() != null && !appointment.getServices().isEmpty()) {
            return appointment.getServices().stream().mapToInt(service -> service.getDurationMinutes() == null
                    ? scheduling.getDefaultVisitDurationMinutes() : service.getDurationMinutes()).sum();
        }
        return appointment.getDurationMinutes() == null || appointment.getDurationMinutes() <= 0
                ? scheduling.getDefaultVisitDurationMinutes() : appointment.getDurationMinutes();
    }

    private void lockBookingKeys(Appointment appointment) {
        if (jdbc == null) return;
        jdbc.execute("select pg_advisory_xact_lock(" + appointment.getUser().getIdUser() + ", 0)");
        if (appointment.getDoctor() != null) {
            jdbc.execute("select pg_advisory_xact_lock(" + appointment.getDoctor().getIdUser() + ", 1)");
        }
    }

    private void validatePatientRestrictions(Integer patientId) {
        PatientBookingRestriction restriction = restrictionRepository == null ? null
                : restrictionRepository.findById(patientId).orElse(null);
        if (restriction != null && restriction.isBlocked()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Запись заблокирована администратором.");
        }
        LocalDateTime since = now().minusDays(scheduling.getNoShowLookbackDays());
        long noShows = repository.findByUser_IdUser(patientId).stream()
                .filter(a -> parseStatus(a.getStatus()) == AppointmentStatus.no_show)
                .filter(a -> a.getAppointmentDate() != null && !a.getAppointmentDate().isBefore(since)).count();
        if (noShows >= scheduling.getNoShowLimit() && (restriction == null || !restriction.isNoShowBlockOverridden())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Запись возможна только через регистратуру");
        }
    }

    private void validatePatientLimits(Appointment appointment, Appointment existing, int duration) {
        Integer patientId = appointment.getUser().getIdUser();
        LocalDateTime current = now();
        List<Appointment> active = repository.findByUser_IdUser(patientId).stream()
                .filter(AppointmentService::isActive)
                .filter(a -> a.getAppointmentDate() != null && a.getAppointmentDate().isAfter(current))
                .filter(a -> isNotExcluded(a, appointment.getIdAppointment())).toList();
        if (active.size() >= scheduling.getMaxActiveTotal()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Нельзя иметь более " + scheduling.getMaxActiveTotal() + " активных записей.");
        }
        if (appointment.getDoctor() != null && active.stream().filter(a -> a.getDoctor() != null
                && appointment.getDoctor().getIdUser().equals(a.getDoctor().getIdUser())).count()
                >= scheduling.getMaxActivePerDoctor()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Нельзя иметь более " + scheduling.getMaxActivePerDoctor() + " активных записей к одному врачу.");
        }
        LocalDateTime end = appointment.getAppointmentDate().plusMinutes(duration);
        if (hasPatientOverlap(patientId, appointment.getAppointmentDate(), end, appointment.getIdAppointment())) {
            throw slotOccupied();
        }
    }

    private boolean hasPatientOverlap(Integer patientId, LocalDateTime start, LocalDateTime end, Integer excludeId) {
        return repository.findByUser_IdUser(patientId).stream()
                .filter(AppointmentService::isActive)
                .filter(a -> isNotExcluded(a, excludeId))
                .filter(a -> a.getAppointmentDate() != null)
                .anyMatch(a -> overlaps(start, end, a.getAppointmentDate(),
                        a.getAppointmentDate().plusMinutes(a.getDurationMinutes() == null
                                ? scheduling.getDefaultVisitDurationMinutes() : a.getDurationMinutes())));
    }

    private boolean hasDoctorOverlap(Integer doctorId, LocalDateTime start, LocalDateTime end, Integer excludeId) {
        LocalDateTime from = start.toLocalDate().atStartOfDay().minusDays(1);
        LocalDateTime to = end.toLocalDate().plusDays(1).atStartOfDay();
        return repository.findByDoctor_IdUserAndAppointmentDateBetween(doctorId, from, to).stream()
                .filter(AppointmentService::isActive)
                .filter(a -> isNotExcluded(a, excludeId))
                .filter(a -> a.getAppointmentDate() != null)
                .anyMatch(a -> overlaps(start, end, a.getAppointmentDate(),
                        a.getAppointmentDate().plusMinutes(a.getDurationMinutes() == null
                                ? scheduling.getDefaultVisitDurationMinutes() : a.getDurationMinutes())));
    }

    private boolean hasActiveHoldOverlap(Integer doctorId, LocalDateTime start, LocalDateTime end,
                                         Integer excludedHoldId, Integer excludedPatientId) {
        if (holdRepository == null) return false;
        return holdRepository.findByDoctor_IdUserAndExpiresAtAfter(doctorId, now()).stream()
                .filter(hold -> !java.util.Objects.equals(hold.getId(), excludedHoldId))
                .filter(hold -> !java.util.Objects.equals(hold.getPatient().getIdUser(), excludedPatientId))
                .anyMatch(hold -> overlaps(start, end, hold.getAppointmentDate(),
                        hold.getAppointmentDate().plusMinutes(hold.getDurationMinutes())));
    }

    private void validateDoctorSchedule(Integer doctorId, LocalDateTime start, LocalDateTime end) {
        List<DoctorScheduleException> exceptions = exceptionRepository == null ? List.of()
                : exceptionRepository.findByDoctor_IdUserAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        doctorId, start.toLocalDate(), start.toLocalDate());
        if (!exceptions.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "В выбранный день врач не принимает.");
        List<DoctorWorkingHours> shifts = shiftsFor(doctorId, start.toLocalDate());
        long duration = java.time.Duration.between(start, end).toMinutes();
        DoctorWorkingHours shift = shifts.stream().filter(s -> !start.toLocalTime().isBefore(s.getStartTime())
                && !end.toLocalTime().isAfter(s.getEndTime())
                && java.time.Duration.between(s.getStartTime(), start.toLocalTime()).toMinutes() % duration == 0)
                .findFirst().orElse(null);
        if (shift == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Выбранное время вне расписания врача.");
        if (breakRepository != null && breakRepository.findByWorkingHours_Id(shift.getId()).stream()
                .anyMatch(b -> overlaps(start.toLocalTime(), end.toLocalTime(), b.getStartTime(), b.getEndTime()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выбранное время пересекается с перерывом врача.");
        }
    }

    private List<DoctorWorkingHours> shiftsFor(Integer doctorId, LocalDate date) {
        if (workingHoursRepository == null || hasScheduleException(doctorId, date)) return List.of();
        int day = date.getDayOfWeek().getValue();
        int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
        DoctorWorkingHours.WeekParity parity = week % 2 == 0
                ? DoctorWorkingHours.WeekParity.EVEN : DoctorWorkingHours.WeekParity.ODD;
        return workingHoursRepository.findByDoctor_IdUserAndDayOfWeek(doctorId, day).stream()
                .filter(shift -> shift.getWeekParity() == null || shift.getWeekParity() == parity).toList();
    }

    private boolean hasScheduleException(Integer doctorId, LocalDate date) {
        return exceptionRepository != null && !exceptionRepository
                .findByDoctor_IdUserAndStartDateLessThanEqualAndEndDateGreaterThanEqual(doctorId, date, date).isEmpty();
    }

    private static boolean overlaps(LocalDateTime aStart, LocalDateTime aEnd, LocalDateTime bStart, LocalDateTime bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    private static boolean isNotExcluded(Appointment appointment, Integer excludeId) {
        return excludeId == null || !java.util.Objects.equals(appointment.getIdAppointment(), excludeId);
    }

    private static boolean overlaps(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    private static AppointmentStatus parseStatus(String value) {
        try { return AppointmentStatus.parse(value); }
        catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    private void validateStatusTransition(Appointment existing, AppointmentStatus next, boolean administrator) {
        if (existing == null) {
            if (next != AppointmentStatus.pending
                    && !(administrator && next == AppointmentStatus.confirmed)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Новая запись должна иметь статус pending.");
            return;
        }
        AppointmentStatus current = parseStatus(existing.getStatus());
        if (current != next && !current.canTransitionTo(next)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Недопустимый переход статуса: " + current.name() + " → " + next.name());
        }
    }

    private static ResponseStatusException slotOccupied() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Это время уже занято, выберите другое");
    }

    private static ResponseStatusException holdExpired() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "Время резерва истекло. Выберите свободный слот повторно.");
    }

    public record SlotDto(String time, boolean available) { }
    public record SlotAvailability(LocalDate date, boolean available, List<SlotDto> slots, LocalDate nearestDate) { }

    public Appointment cancelByPatient(Integer appointmentId) {
        Appointment appointment = repository.findById(appointmentId).orElse(null);
        if (appointment == null) return null;
        AppointmentStatus current = parseStatus(appointment.getStatus());
        validateCancellableAppointment(appointment, current);
        if (appointment.getAppointmentDate().minusHours(scheduling.getCancellationDeadlineHours()).isBefore(now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Отмена возможна только по телефону регистратуры");
        }
        repository.delete(appointment);
        repository.flush();
        return null;
    }

    public Appointment cancelByStaff(Integer appointmentId) {
        Appointment appointment = repository.findById(appointmentId).orElse(null);
        if (appointment == null) return null;
        AppointmentStatus current = parseStatus(appointment.getStatus());
        validateCancellableAppointment(appointment, current);
        appointment.setStatus(AppointmentStatus.cancelled.name());
        return repository.saveAndFlush(appointment);
    }

    private void validateCancellableAppointment(Appointment appointment, AppointmentStatus current) {
        if (appointment.getAppointmentDate() != null && appointment.getAppointmentDate().isBefore(now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Нельзя отменить прошедший приём.");
        }
        if (current == AppointmentStatus.completed || current == AppointmentStatus.no_show
                || current == AppointmentStatus.cancelled) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Этот статус записи нельзя отменить.");
        }
        if (!current.canTransitionTo(AppointmentStatus.cancelled)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Недопустимый переход статуса записи.");
        }
    }

    /** Совместимость для внутренних вызовов: отмена с правилами пациента. */
    public Appointment cancel(Integer appointmentId) {
        return cancelByPatient(appointmentId);
    }

    public boolean isDoctorAvailable(Integer doctorId, LocalDateTime appointmentDate, Integer excludeAppointmentId) {
        if (doctorId == null || appointmentDate == null) return true;
        return !hasDoctorOverlap(doctorId, appointmentDate,
                appointmentDate.plusMinutes(scheduling.getDefaultVisitDurationMinutes()), excludeAppointmentId);
    }

    public void deleteById(Integer id) {
        repository.deleteById(id);
    }

    public Page<Appointment> search(String fieldName, String value, int page) {
        if (fieldName == null || value == null || value.isBlank()) {
            return findPage(page);
        }

        String trimmedValue = value.trim();
        if ("id".equals(fieldName) || "idAppointment".equals(fieldName)) {
            return parseId(trimmedValue)
                    .map(id -> repository.findById(id).map(List::of).orElseGet(List::of))
                    .map(items -> PaginationHelper.pageFromList(items, page))
                    .orElseGet(() -> PaginationHelper.pageFromList(List.of(), page));
        }

        if ("name".equals(fieldName) || "status".equals(fieldName)) {
            return PaginationHelper.pageFromList(repository.findByStatus(trimmedValue), page);
        }

        return PaginationHelper.pageFromList(repository.searchByKeyword(trimmedValue), page);
    }

    public Page<Appointment> searchAdmin(String fieldName, String value, String status, int page) {
        List<Appointment> appointments = repository.findAll();
        if (value != null && !value.isBlank()) {
            String query = value.trim().toLowerCase();
            appointments = appointments.stream().filter(appointment -> switch (fieldName == null ? "all" : fieldName) {
                case "patient" -> appointment.getUser() != null
                        && appointment.getUser().getDisplayName().toLowerCase().contains(query);
                case "doctor" -> appointment.getDoctor() != null
                        && appointment.getDoctor().getDisplayName().toLowerCase().contains(query);
                case "department" -> appointment.getDepartment() != null
                        && appointment.getDepartment().getDepartmentName().toLowerCase().contains(query);
                default -> (appointment.getUser() != null
                        && appointment.getUser().getDisplayName().toLowerCase().contains(query))
                        || (appointment.getDoctor() != null
                        && appointment.getDoctor().getDisplayName().toLowerCase().contains(query))
                        || (appointment.getDepartment() != null
                        && appointment.getDepartment().getDepartmentName().toLowerCase().contains(query))
                        || (appointment.getComplaint() != null
                        && appointment.getComplaint().toLowerCase().contains(query));
            }).toList();
        }
        if (status != null && !status.isBlank()) {
            appointments = appointments.stream()
                    .filter(appointment -> appointment.getStatus() != null
                            && appointment.getStatus().equalsIgnoreCase(status))
                    .toList();
        }
        return PaginationHelper.pageFromList(appointments, page);
    }

    public Page<Appointment> searchForUser(Integer userId, String fieldName, String value, int page) {
        return searchForUser(userId, fieldName, value, page, null);
    }

    public Page<Appointment> searchForUser(Integer userId, String fieldName, String value, int page,
                                           String visitPeriod) {
        List<Appointment> appointments = repository.findByUser_IdUser(userId);
        if (fieldName != null && value != null && !value.isBlank()) {
            String query = value.trim();
            if ("id".equals(fieldName) || "idAppointment".equals(fieldName)) {
                appointments = parseId(query)
                        .flatMap(repository::findById)
                        .filter(appointment -> appointment.getUser() != null
                                && userId.equals(appointment.getUser().getIdUser()))
                        .map(List::of)
                        .orElseGet(List::of);
            } else if ("name".equals(fieldName) || "status".equals(fieldName)) {
                appointments = appointments.stream()
                        .filter(appointment -> appointment.getStatus() != null
                                && appointment.getStatus().equalsIgnoreCase(query))
                        .toList();
            } else if ("doctor".equals(fieldName)) {
                String lowerQuery = query.toLowerCase();
                appointments = appointments.stream()
                        .filter(appointment -> appointment.getDoctor() != null
                                && ((appointment.getDoctor().getDisplayName() != null
                                && appointment.getDoctor().getDisplayName().toLowerCase().contains(lowerQuery))
                                || (appointment.getDoctor().getSpecialty() != null
                                && appointment.getDoctor().getSpecialty().getSpecialtyName() != null
                                && appointment.getDoctor().getSpecialty().getSpecialtyName()
                                .toLowerCase().contains(lowerQuery))))
                        .toList();
            } else {
                String lowerQuery = query.toLowerCase();
                appointments = appointments.stream()
                        .filter(appointment -> (appointment.getComplaint() != null
                                && appointment.getComplaint().toLowerCase().contains(lowerQuery))
                                || (appointment.getStatus() != null
                                && appointment.getStatus().toLowerCase().contains(lowerQuery)))
                        .toList();
            }
        }
        LocalDateTime now = now();
        if ("past".equals(visitPeriod)) {
            appointments = appointments.stream()
                    .filter(appointment -> appointment.getAppointmentDate() != null
                            && appointment.getAppointmentDate().isBefore(now))
                    .toList();
        } else if ("upcoming".equals(visitPeriod)) {
            appointments = appointments.stream()
                    .filter(appointment -> appointment.getAppointmentDate() != null
                            && appointment.getAppointmentDate().isAfter(now))
                    .filter(AppointmentService::isActive)
                    .toList();
        }
        return PaginationHelper.pageFromList(appointments, page);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(CLINIC_ZONE);
    }

    public List<Appointment> findByStatus(String status) {
        return repository.findByStatus(status);
    }

    public List<Appointment> findByUser(Integer idUser) {
        return repository.findByUser_IdUser(idUser);
    }

    public List<Appointment> findByDoctor(Integer doctorId) {
        return repository.findByDoctor_IdUser(doctorId);
    }

    public List<Appointment> findByDateRange(LocalDateTime start, LocalDateTime end) {
        return repository.findByAppointmentDateBetween(start, end);
    }

    public List<Appointment> findUpcomingScheduled(LocalDateTime from) {
        return repository.findByAppointmentDateAfter(from).stream()
                .filter(appointment -> parseStatus(appointment.getStatus()) == AppointmentStatus.pending
                        || parseStatus(appointment.getStatus()) == AppointmentStatus.confirmed)
                .sorted(java.util.Comparator.comparing(Appointment::getAppointmentDate))
                .toList();
    }

    private Optional<Integer> parseId(String value) {
        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
