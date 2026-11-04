package com.example.medicalsystem.appointment.service;

import com.example.medicalsystem.appointment.model.Appointment;
import com.example.medicalsystem.appointment.repository.AppointmentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppointmentOwnershipTest {
    @Test
    void patientCanReadOwnAppointment() {
        var repo = mock(AppointmentRepository.class);
        var jdbc = mock(JdbcTemplate.class);
        var appointment = appointment(42);
        when(repo.findById(7)).thenReturn(Optional.of(appointment));
        when(jdbc.query(anyString(), ArgumentMatchers.<ResultSetExtractor<Integer>>any(), eq("patient"))).thenReturn(42);
        var service = new AppointmentService(repo, jdbc);

        assertSame(appointment, service.getVisible(7, authentication("patient", "PATIENT")));
    }

    @Test
    void patientCannotReadAnotherPatientsAppointment() {
        var repo = mock(AppointmentRepository.class);
        var jdbc = mock(JdbcTemplate.class);
        when(repo.findById(7)).thenReturn(Optional.of(appointment(9)));
        when(jdbc.query(anyString(), ArgumentMatchers.<ResultSetExtractor<Integer>>any(), eq("patient"))).thenReturn(42);
        var service = new AppointmentService(repo, jdbc);

        assertThrows(AccessDeniedException.class,
                () -> service.getVisible(7, authentication("patient", "PATIENT")));
    }

    @Test
    void patientCannotUpdateOrDeleteAnotherPatientsAppointment() {
        var repo = mock(AppointmentRepository.class);
        var jdbc = mock(JdbcTemplate.class);
        when(repo.findById(7)).thenReturn(Optional.of(appointment(9)));
        when(jdbc.query(anyString(), ArgumentMatchers.<ResultSetExtractor<Integer>>any(), eq("patient"))).thenReturn(42);
        var service = new AppointmentService(repo, jdbc);
        var patient = authentication("patient", "PATIENT");
        var request = new AppointmentService.AppointmentRequest(null, null, null, null, null, null, null, null);

        assertThrows(AccessDeniedException.class, () -> service.update(7, request, patient));
        assertThrows(AccessDeniedException.class, () -> service.delete(7, patient));
        verify(repo, never()).save(org.mockito.ArgumentMatchers.any());
        verify(repo, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void serviceBookingRequiresAtLeastOneService() {
        var repo = mock(AppointmentRepository.class);
        var service = new AppointmentService(repo, mock(JdbcTemplate.class));
        var request = new AppointmentService.AppointmentRequest(null, null, null, null, null, null,
                com.example.medicalsystem.appointment.model.AppointmentType.SERVICE, List.of());

        var error = assertThrows(ResponseStatusException.class,
                () -> service.create(request, authentication("patient", "PATIENT")));

        assertEquals(400, error.getStatusCode().value());
        verify(repo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doctorVisitRequiresDepartmentAndDoctor() {
        var repo = mock(AppointmentRepository.class);
        var service = new AppointmentService(repo, mock(JdbcTemplate.class));
        var request = new AppointmentService.AppointmentRequest(null, null, null, null, null, null,
                com.example.medicalsystem.appointment.model.AppointmentType.DOCTOR_VISIT, List.of());

        var error = assertThrows(ResponseStatusException.class,
                () -> service.create(request, authentication("patient", "PATIENT")));

        assertEquals(400, error.getStatusCode().value());
        verify(repo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private static Appointment appointment(Integer patientId) {
        Appointment value = new Appointment();
        value.setUserId(patientId);
        value.setDoctorId(63);
        return value;
    }

    private static UsernamePasswordAuthenticationToken authentication(String username, String role) {
        return new UsernamePasswordAuthenticationToken(username, "", List.of(new SimpleGrantedAuthority(role)));
    }
}
