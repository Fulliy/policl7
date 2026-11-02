package com.example.medicalsystem.appointment.service;

import com.example.medicalsystem.appointment.model.Appointment;
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
        validateDepartment(request.departmentId());
        Appointment value = new Appointment();
        value.setAppointmentDate(request.appointmentDate());
        value.setComplaint(request.complaint());
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
        return appointments.save(value);
    }

    public Appointment update(Integer id, AppointmentRequest request, Authentication auth) {
        Appointment value = require(id);
        checkOwnerOrAssignedDoctor(value, auth);
        validateDepartment(request.departmentId());
        value.setAppointmentDate(request.appointmentDate());
        value.setComplaint(request.complaint());
        value.setDepartmentId(request.departmentId());
        if (!isPatient(auth)) {
            value.setStatus(request.status());
            if (request.userId() != null) value.setUserId(requireUserId(request.userId()));
            if (has(auth, "ADMIN")) value.setDoctorId(request.doctorId());
        }
        return appointments.save(value);
    }

    public void delete(Integer id, Authentication auth) {
        Appointment value = require(id);
        checkOwnerOrAssignedDoctor(value, auth);
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
                                     Integer userId, Integer departmentId, Integer doctorId) { }
}
