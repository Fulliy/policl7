package com.example.medicalsystem.appointment.service;

import com.example.medicalsystem.appointment.model.Appointment;
import com.example.medicalsystem.appointment.model.AppointmentType;
import com.example.medicalsystem.appointment.repository.AppointmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.time.temporal.WeekFields;
import java.util.Set;
import java.util.ArrayList;

@Service
@Transactional
public class AppointmentService {
    private final AppointmentRepository appointments;
    private final JdbcTemplate jdbc;
    @Value("${app.scheduling.min-notice-minutes:60}") private int minNoticeMinutes;
    @Value("${app.scheduling.max-advance-days:30}") private int maxAdvanceDays;
    @Value("${app.scheduling.max-active-per-doctor:3}") private int maxActivePerDoctor;
    @Value("${app.scheduling.max-active-total:5}") private int maxActiveTotal;
    @Value("${app.scheduling.cancellation-deadline-hours:2}") private int cancellationDeadlineHours;
    @Value("${app.scheduling.no-show-lookback-days:90}") private int noShowLookbackDays;
    @Value("${app.scheduling.no-show-limit:3}") private int noShowLimit;
    @Value("${app.scheduling.default-visit-duration-minutes:30}") private int defaultDurationMinutes;
    @Value("${app.scheduling.nearest-date-search-days:90}") private int nearestDateSearchDays;

    public AppointmentService(AppointmentRepository appointments, JdbcTemplate jdbc) {
        this.appointments = appointments;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Appointment> findVisible(Authentication auth) {
        Integer userId = currentUserId(auth);
        if (has(auth, "ADMIN")) return appointments.findAll();
        if (has(auth, "DOCTOR")) return appointments.findByDoctorId(userId);
        if (has(auth, "PATIENT")) return appointments.findByUserId(userId);
        throw new AccessDeniedException("Недостаточно прав");
    }

    @Transactional(readOnly = true)
    public Appointment getVisible(Integer id, Authentication auth) {
        Appointment value = require(id);
        checkOwnerOrAssignedDoctor(value, auth);
        return value;
    }

    public Appointment create(AppointmentRequest request, Authentication auth) {
        AppointmentType type = request.type() == null ? AppointmentType.DOCTOR_VISIT : request.type();
        validateType(request, type, auth);
        Integer patientId = isPatient(auth) ? currentUserId(auth) : request.userId();
        Integer doctorId = has(auth, "DOCTOR") ? currentUserId(auth) : request.doctorId();
        String targetStatus = isPatient(auth) ? "pending" : AppointmentStatusRules.normalize(request.status());
        if (!"pending".equals(targetStatus)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Новая запись должна иметь статус pending.");
        int duration = requestDuration(request.serviceIds());
        validateAppointmentTime(request, type, patientId, doctorId, null, true, duration);
        if (!isPatient(auth) && (request.status() == null || request.status().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите статус записи.");
        }
        Appointment value = new Appointment();
        value.setAppointmentDate(request.appointmentDate());
        value.setComplaint(request.complaint());
        value.setType(type);
        value.setDurationMinutes(duration);
        value.setDepartmentId(request.departmentId());
        if (isPatient(auth)) {
            value.setUserId(currentUserId(auth));
            value.setStatus("pending");
        } else if (has(auth, "DOCTOR")) {
            value.setUserId(requireUserId(request.userId()));
            value.setDoctorId(currentUserId(auth));
            value.setStatus(request.status());
        } else if (has(auth, "ADMIN")) {
            value.setUserId(requireUserId(request.userId()));
            value.setDoctorId(request.doctorId());
            value.setStatus(request.status());
        } else {
            throw new AccessDeniedException("Недостаточно прав");
        }
        if (type == AppointmentType.DOCTOR_VISIT && isPatient(auth)) value.setDoctorId(request.doctorId());
        if (type == AppointmentType.SERVICE) value.setDoctorId(null);
        return persistBooking(value, request.serviceIds());
    }

    public Appointment update(Integer id, AppointmentRequest request, Authentication auth) {
        Appointment value = require(id);
        checkOwnerOrAssignedDoctor(value, auth);
        AppointmentType type = request.type() == null ? value.getType() : request.type();
        validateType(request, type, auth);
        Integer doctorId = has(auth, "DOCTOR") ? currentUserId(auth) : request.doctorId();
        String targetStatus = isPatient(auth) ? value.getStatus() : AppointmentStatusRules.normalize(request.status());
        if (!AppointmentStatusRules.canTransition(value.getStatus(), targetStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Недопустимый переход статуса записи.");
        }
        if (isActiveStatus(value.getStatus()) && isActiveStatus(targetStatus)
                && (!java.util.Objects.equals(value.getAppointmentDate(), request.appointmentDate())
                || !java.util.Objects.equals(value.getDoctorId(), doctorId) || value.getType() != type)
                && value.getAppointmentDate().minusHours(cancellationDeadlineHours).isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Отмена возможна только по телефону регистратуры");
        }
        int duration = requestDuration(request.serviceIds());
        validateAppointmentTime(request, type, value.getUserId(), doctorId, id,
                isPatient(auth) || isActiveStatus(targetStatus), duration);
        if (!isPatient(auth) && (request.status() == null || request.status().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите статус записи.");
        }
        value.setAppointmentDate(request.appointmentDate());
        value.setComplaint(request.complaint());
        value.setType(type);
        value.setDurationMinutes(duration);
        value.setDepartmentId(request.departmentId());
        if (!isPatient(auth)) {
            value.setStatus(targetStatus);
            if (request.userId() != null) value.setUserId(requireUserId(request.userId()));
            if (has(auth, "ADMIN")) value.setDoctorId(request.doctorId());
            if (has(auth, "DOCTOR")) value.setDoctorId(currentUserId(auth));
        }
        if (type == AppointmentType.DOCTOR_VISIT && isPatient(auth)) value.setDoctorId(request.doctorId());
        if (type == AppointmentType.SERVICE) value.setDoctorId(null);
        return persistBooking(value, request.serviceIds());
    }

    public void delete(Integer id, Authentication auth) {
        Appointment value = require(id);
        checkOwnerOrAssignedDoctor(value, auth);
        jdbc.update("delete from appointment_services where id_appointment=?", id);
        appointments.delete(value);
    }

    public Appointment cancel(Integer id, Authentication auth) {
        Appointment value = require(id);
        checkOwnerOrAssignedDoctor(value, auth);
        if (!AppointmentStatusRules.canTransition(AppointmentStatusRules.normalize(value.getStatus()), "cancelled")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Недопустимый переход статуса записи.");
        }
        if (value.getAppointmentDate().minusHours(cancellationDeadlineHours).isBefore(java.time.LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Отмена возможна только по телефону регистратуры");
        }
        value.setStatus("cancelled");
        return appointments.saveAndFlush(value);
    }

    private void checkOwnerOrAssignedDoctor(Appointment value, Authentication auth) {
        if (has(auth, "ADMIN")) return;
        Integer userId = currentUserId(auth);
        if (has(auth, "DOCTOR") && userId.equals(value.getDoctorId())) return;
        if (isPatient(auth) && userId.equals(value.getUserId())) return;
        throw new AccessDeniedException("Доступ к чужой записи запрещён");
    }

    private Integer currentUserId(Authentication auth) {
        Integer id = jdbc.query("select id_user from users where username = ?",
                rs -> rs.next() ? rs.getInt(1) : null, auth.getName());
        if (id == null) throw new AccessDeniedException("Пользователь не найден");
        return id;
    }

    private Integer requireUserId(Integer id) {
        if (id == null || !Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from users where id_user = ?)", Boolean.class, id))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пользователь не найден");
        }
        return id;
    }

    private void validateDepartment(Integer id) {
        if (id == null || !Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from departments where id_department = ?)", Boolean.class, id))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Отделение не найдено");
        }
    }

    private void validateType(AppointmentRequest request, AppointmentType type, Authentication auth) {
        List<Integer> ids = request.serviceIds() == null ? List.of() : request.serviceIds().stream().distinct().toList();
        if (type == AppointmentType.DOCTOR_VISIT) {
            if (request.departmentId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Для приёма врача выберите отделение.");
            validateDepartment(request.departmentId());
            Integer doctorId = has(auth, "DOCTOR") ? currentUserId(auth) : request.doctorId();
            if (doctorId == null || !Boolean.TRUE.equals(jdbc.queryForObject(
                    "select exists(select 1 from users u join user_role r on r.user_id=u.id_user "
                            + "where u.id_user=? and u.id_department=? and r.roles='DOCTOR')",
                    Boolean.class, doctorId, request.departmentId()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Для приёма врача выберите врача из выбранного отделения.");
            }
            if (!ids.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Для приёма врача нельзя передавать услуги.");
        } else if (type == AppointmentType.SERVICE) {
            if (request.departmentId() != null || request.doctorId() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Для записи на услуги нельзя указывать врача или отделение.");
            }
            if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Выберите хотя бы одну услугу.");
            Integer found = jdbc.queryForObject("select count(distinct id_service) from services where id_service in ("
                    + ids.stream().map(x -> "?").collect(java.util.stream.Collectors.joining(",")) + ")",
                    Integer.class, ids.toArray());
            if (found == null || found != ids.size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Одна или несколько выбранных услуг не найдены.");
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестный тип записи.");
        }
    }

    private void replaceServices(Integer appointmentId, List<Integer> serviceIds) {
        jdbc.update("delete from appointment_services where id_appointment=?", appointmentId);
        if (serviceIds == null) return;
        serviceIds.stream().distinct().forEach(serviceId -> jdbc.update(
                "insert into appointment_services (id_appointment, id_service) values (?, ?)", appointmentId, serviceId));
    }

    private Appointment persistBooking(Appointment value, List<Integer> serviceIds) {
        try {
            if (value.getStatus() == null) value.setStatus("pending");
            Appointment saved = appointments.saveAndFlush(value);
            replaceServices(saved.getIdAppointment(), serviceIds);
            return saved;
        } catch (DataIntegrityViolationException exception) {
            throw slotOccupied();
        }
    }

    private void validateAppointmentTime(AppointmentRequest request, AppointmentType type,
                                         Integer patientId, Integer doctorId, Integer excludeId, boolean active,
                                         int durationMinutes) {
        java.time.LocalDateTime dateTime = request.appointmentDate();
        if (dateTime == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Дата и время обязательны.");
        }
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (active && dateTime.isBefore(now)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Слоты в прошлом недоступны.");
        if (!active) return;
        if (dateTime.isBefore(now.plusMinutes(minNoticeMinutes))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Запись возможна не менее чем за " + minNoticeMinutes + " минут до приёма.");
        if (dateTime.isAfter(now.plusDays(maxAdvanceDays))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Запись доступна не далее чем на " + maxAdvanceDays + " дней вперёд.");
        if (patientId != null) {
            jdbc.execute("select pg_advisory_xact_lock(" + patientId + ", 0)");
            validatePatient(patientId, doctorId, dateTime, dateTime.plusMinutes(durationMinutes), excludeId);
        }
        if (type == AppointmentType.DOCTOR_VISIT) {
            if (doctorId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выберите врача.");
            jdbc.execute("select pg_advisory_xact_lock(" + doctorId + ", 1)");
            validateDoctorSchedule(doctorId, dateTime, dateTime.plusMinutes(durationMinutes));
            if (hasOverlap("id_doctor", doctorId, dateTime, dateTime.plusMinutes(durationMinutes), excludeId)) throw slotOccupied();
        }
    }

    private static String activePredicate() {
        return "status in ('pending','confirmed')";
    }

    private static boolean isActiveStatus(String status) {
        return status == null || Set.of("pending", "confirmed", "запланирован", "запланировано", "подтвержден", "подтверждён")
                .contains(status.toLowerCase(java.util.Locale.ROOT));
    }

    private int requestDuration(List<Integer> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) return defaultDurationMinutes;
        String placeholders = serviceIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
        Integer duration = jdbc.queryForObject("select coalesce(sum(duration_minutes), ?) from services where id_service in ("
                + placeholders + ")", Integer.class, prepend(defaultDurationMinutes, serviceIds));
        return duration == null || duration < 1 ? defaultDurationMinutes : duration;
    }

    private Object[] prepend(Integer first, List<Integer> values) {
        Object[] args = new Object[values.size() + 1]; args[0] = first;
        for (int i=0;i<values.size();i++) args[i+1] = values.get(i);
        return args;
    }

    private void validatePatient(Integer patientId, Integer doctorId, java.time.LocalDateTime start,
                                  java.time.LocalDateTime end, Integer excludeId) {
        Boolean blocked = jdbc.queryForObject("select coalesce((select blocked from patient_booking_restrictions where id_user=?),false)",
                Boolean.class, patientId);
        Integer noShowCount = jdbc.queryForObject("select count(*) from appointments where id_user=? and status='no_show' "
                + "and appointment_date >= ?", Integer.class, patientId,
                java.time.LocalDateTime.now().minusDays(noShowLookbackDays));
        Boolean override = jdbc.queryForObject("select coalesce((select no_show_block_overridden from patient_booking_restrictions where id_user=?),false)",
                Boolean.class, patientId);
        if (Boolean.TRUE.equals(blocked)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Запись заблокирована администратором.");
        if (noShowCount != null && noShowCount >= noShowLimit && !Boolean.TRUE.equals(override)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Запись возможна только через регистратуру");
        }
        Integer total = jdbc.queryForObject("select count(*) from appointments where id_user=? and " + activePredicate()
                + " and id_appointment<>coalesce(?, -1)", Integer.class, patientId, excludeId);
        if (total != null && total >= maxActiveTotal) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Нельзя иметь более " + maxActiveTotal + " активных записей.");
        if (doctorId != null) {
            Integer perDoctor = jdbc.queryForObject("select count(*) from appointments where id_user=? and id_doctor=? and "
                    + activePredicate() + " and id_appointment<>coalesce(?, -1)", Integer.class, patientId, doctorId, excludeId);
            if (perDoctor != null && perDoctor >= maxActivePerDoctor) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Нельзя иметь более " + maxActivePerDoctor + " активных записей к одному врачу.");
        }
        if (hasOverlap("id_user", patientId, start, end, excludeId)) throw slotOccupied();
    }

    private boolean hasOverlap(String column, Integer ownerId, java.time.LocalDateTime start,
                               java.time.LocalDateTime end, Integer excludeId) {
        if (!Set.of("id_user", "id_doctor").contains(column)) throw new IllegalArgumentException("Invalid owner column");
        Boolean exists = jdbc.queryForObject("select exists(select 1 from appointments where " + column + "=? and "
                        + activePredicate() + " and id_appointment<>coalesce(?, -1) and appointment_date < ? "
                        + "and appointment_date + make_interval(mins => duration_minutes) > ?)", Boolean.class,
                ownerId, excludeId, end, start);
        return Boolean.TRUE.equals(exists);
    }

    private void validateDoctorSchedule(Integer doctorId, java.time.LocalDateTime start, java.time.LocalDateTime end) {
        LocalDate date = start.toLocalDate();
        Boolean exception = jdbc.queryForObject("select exists(select 1 from doctor_schedule_exceptions "
                + "where id_doctor=? and start_date<=? and end_date>=?)", Boolean.class, doctorId, date, date);
        if (Boolean.TRUE.equals(exception)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "В выбранный день врач не принимает.");
        int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
        String parity = week % 2 == 0 ? "EVEN" : "ODD";
        Boolean within = jdbc.queryForObject("select exists(select 1 from doctor_working_hours where id_doctor=? "
                + "and day_of_week=? and (week_parity is null or week_parity=?) and start_time<=? and end_time>=?)",
                Boolean.class, doctorId, date.getDayOfWeek().getValue(), parity, start.toLocalTime(), end.toLocalTime());
        if (!Boolean.TRUE.equals(within)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выбранное время вне расписания врача.");
        Boolean pause = jdbc.queryForObject("select exists(select 1 from doctor_schedule_breaks b "
                + "join doctor_working_hours h using (id_working_hours) where h.id_doctor=? and h.day_of_week=? "
                + "and (h.week_parity is null or h.week_parity=?) and b.start_time<? and b.end_time>?)",
                Boolean.class, doctorId, date.getDayOfWeek().getValue(), parity, end.toLocalTime(), start.toLocalTime());
        if (Boolean.TRUE.equals(pause)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выбранное время пересекается с перерывом врача.");
    }

    private static ResponseStatusException slotOccupied() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Это время уже занято, выберите другое");
    }

    public SlotAvailability availability(Integer doctorId, LocalDate date) {
        List<SlotDto> values = slotsFor(doctorId, date, defaultDurationMinutes);
        boolean available = values.stream().anyMatch(SlotDto::available);
        LocalDate nearest = null;
        if (!available) for (int i=1; i<=nearestDateSearchDays && nearest==null; i++) {
            LocalDate candidate = date.plusDays(i);
            if (slotsFor(doctorId, candidate, defaultDurationMinutes).stream().anyMatch(SlotDto::available)) nearest = candidate;
        }
        return new SlotAvailability(date, available, values, nearest);
    }

    private List<SlotDto> slotsFor(Integer doctorId, LocalDate date, int duration) {
        if (date.isBefore(LocalDate.now()) || date.isAfter(LocalDate.now().plusDays(maxAdvanceDays))) return List.of();
        Boolean exception = jdbc.queryForObject("select exists(select 1 from doctor_schedule_exceptions where id_doctor=? and start_date<=? and end_date>=?)",
                Boolean.class, doctorId, date, date);
        if (Boolean.TRUE.equals(exception)) return List.of();
        int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
        String parity = week % 2 == 0 ? "EVEN" : "ODD";
        List<Shift> shifts = jdbc.query("select id_working_hours,start_time,end_time from doctor_working_hours "
                        + "where id_doctor=? and day_of_week=? and (week_parity is null or week_parity=?) order by start_time",
                (rs, row) -> new Shift(rs.getInt(1), rs.getTime(2).toLocalTime(), rs.getTime(3).toLocalTime()),
                doctorId, date.getDayOfWeek().getValue(), parity);
        List<SlotDto> result = new ArrayList<>();
        for (Shift shift : shifts) {
            List<BreakInterval> breaks = jdbc.query("select start_time,end_time from doctor_schedule_breaks where id_working_hours=?",
                    (rs,row) -> new BreakInterval(rs.getTime(1).toLocalTime(),rs.getTime(2).toLocalTime()), shift.id());
            for (LocalTime time=shift.start(); !time.plusMinutes(duration).isAfter(shift.end()); time=time.plusMinutes(duration)) {
                LocalTime slotStart=time, slotEnd=time.plusMinutes(duration);
                LocalDateTime start=date.atTime(slotStart), end=date.atTime(slotEnd);
                boolean breakConflict=breaks.stream().anyMatch(b -> slotStart.isBefore(b.end()) && b.start().isBefore(slotEnd));
                Boolean busy=jdbc.queryForObject("select exists(select 1 from appointments where id_doctor=? and "
                                + "appointment_type='DOCTOR_VISIT' and " + activePredicate() + " and appointment_date < ? "
                                + "and appointment_date + make_interval(mins => duration_minutes) > ?)", Boolean.class,
                        doctorId,end,start);
                boolean within=!start.isBefore(java.time.LocalDateTime.now().plusMinutes(minNoticeMinutes))
                        && !start.isAfter(java.time.LocalDateTime.now().plusDays(maxAdvanceDays));
                result.add(new SlotDto(time.toString(), within && !breakConflict && !Boolean.TRUE.equals(busy)));
            }
        }
        return result;
    }

    private record Shift(Integer id, LocalTime start, LocalTime end) { }
    private record BreakInterval(LocalTime start, LocalTime end) { }

    public record SlotDto(String time, boolean available) { }
    public record SlotAvailability(LocalDate date, boolean available, List<SlotDto> slots, LocalDate nearestDate) { }

    @Transactional(readOnly = true)
    public List<ServiceSummary> servicesFor(Integer appointmentId) {
        return jdbc.query("select s.id_service, s.service_name, s.cost from appointment_services aps "
                        + "join services s on s.id_service=aps.id_service where aps.id_appointment=? order by s.id_service",
                (rs, row) -> new ServiceSummary(rs.getInt(1), rs.getString(2), rs.getInt(3)), appointmentId);
    }

    public record ServiceSummary(Integer id, String name, Integer cost) { }

    private Appointment require(Integer id) {
        return appointments.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static boolean has(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    private static boolean isPatient(Authentication auth) {
        return has(auth, "PATIENT") && !has(auth, "DOCTOR") && !has(auth, "ADMIN");
    }

    public record AppointmentRequest(java.time.LocalDateTime appointmentDate, String status, String complaint,
                                     Integer userId, Integer departmentId, Integer doctorId,
                                     AppointmentType type, List<Integer> serviceIds) { }
}
