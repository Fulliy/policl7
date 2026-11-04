package com.example.medicalsystem.appointment.service;

import com.example.medicalsystem.appointment.model.Appointment;
import com.example.medicalsystem.appointment.model.AppointmentType;
import com.example.medicalsystem.appointment.repository.AppointmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional
public class AppointmentService {
    private final AppointmentRepository appointments;
    private final JdbcTemplate jdbc;

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
        if (!isPatient(auth) && (request.status() == null || request.status().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите статус записи.");
        }
        Appointment value = new Appointment();
        value.setAppointmentDate(request.appointmentDate());
        value.setComplaint(request.complaint());
        value.setType(type);
        value.setDepartmentId(request.departmentId());
        if (isPatient(auth)) {
            value.setUserId(currentUserId(auth));
            value.setStatus("Запланирован");
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
        Appointment saved = appointments.save(value);
        replaceServices(saved.getIdAppointment(), request.serviceIds());
        return saved;
    }

    public Appointment update(Integer id, AppointmentRequest request, Authentication auth) {
        Appointment value = require(id);
        checkOwnerOrAssignedDoctor(value, auth);
        AppointmentType type = request.type() == null ? value.getType() : request.type();
        validateType(request, type, auth);
        if (!isPatient(auth) && (request.status() == null || request.status().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите статус записи.");
        }
        value.setAppointmentDate(request.appointmentDate());
        value.setComplaint(request.complaint());
        value.setType(type);
        value.setDepartmentId(request.departmentId());
        if (!isPatient(auth)) {
            value.setStatus(request.status());
            if (request.userId() != null) value.setUserId(requireUserId(request.userId()));
            if (has(auth, "ADMIN")) value.setDoctorId(request.doctorId());
            if (has(auth, "DOCTOR")) value.setDoctorId(currentUserId(auth));
        }
        if (type == AppointmentType.DOCTOR_VISIT && isPatient(auth)) value.setDoctorId(request.doctorId());
        if (type == AppointmentType.SERVICE) value.setDoctorId(null);
        Appointment saved = appointments.save(value);
        replaceServices(saved.getIdAppointment(), request.serviceIds());
        return saved;
    }

    public void delete(Integer id, Authentication auth) {
        Appointment value = require(id);
        checkOwnerOrAssignedDoctor(value, auth);
        jdbc.update("delete from appointment_services where id_appointment=?", id);
        appointments.delete(value);
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
