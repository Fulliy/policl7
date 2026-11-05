package com.example.medicalsystem.controller;

import com.example.medicalsystem.model.*;
import com.example.medicalsystem.repository.*;
import com.example.medicalsystem.service.AppointmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(AdminSchedulingControllerTest.TestConfig.class)
class AdminSchedulingControllerTest {
    @Configuration
    @EnableMethodSecurity
    static class TestConfig {
        @Bean AppointmentService appointmentService() { return mock(AppointmentService.class); }
        @Bean AppointmentRepository appointmentRepository() { return mock(AppointmentRepository.class); }
        @Bean DoctorScheduleExceptionRepository exceptionRepository() { return mock(DoctorScheduleExceptionRepository.class); }
        @Bean PatientBookingRestrictionRepository restrictionRepository() { return mock(PatientBookingRestrictionRepository.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
        @Bean AdminSchedulingController adminSchedulingController(AppointmentService service,
                AppointmentRepository appointments, DoctorScheduleExceptionRepository exceptions,
                PatientBookingRestrictionRepository restrictions, UserRepository users) {
            return new AdminSchedulingController(service, appointments, exceptions, restrictions, users);
        }
    }

    @jakarta.annotation.Resource private AdminSchedulingController controller;
    @jakarta.annotation.Resource private AppointmentRepository appointmentRepository;
    @jakarta.annotation.Resource private DoctorScheduleExceptionRepository exceptionRepository;
    @jakarta.annotation.Resource private UserRepository userRepository;
    @jakarta.annotation.Resource private AppointmentService appointmentService;
    @jakarta.annotation.Resource private PatientBookingRestrictionRepository restrictionRepository;

    @AfterEach void clearSecurity() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }

    @Test
    void administratorCanListAppointmentsNeedingReschedule() {
        authenticate("ADMIN");
        when(appointmentRepository.findByRequiresRescheduleTrue()).thenReturn(List.of());

        assertTrue(controller.requiringReschedule().isEmpty());
    }

    @Test
    void patientCannotCallAdminEndpointToClearNoShowRestriction() {
        authenticate("PATIENT");

        assertThrows(AccessDeniedException.class, () -> controller.clearNoShowBlock(22));
        verifyNoInteractions(userRepository);
    }

    @Test
    void administratorCanLiftNoShowBlock() {
        authenticate("ADMIN");
        when(userRepository.existsById(22)).thenReturn(true);
        when(restrictionRepository.findById(22)).thenReturn(Optional.empty());

        controller.clearNoShowBlock(22);

        verify(restrictionRepository).save(argThat(PatientBookingRestriction::isNoShowBlockOverridden));
    }

    @Test
    void addingDoctorLeaveMarksExistingActiveAppointmentsForReschedule() {
        authenticate("ADMIN");
        User doctor = new User();
        doctor.setIdUser(5);
        doctor.setRoles(EnumSet.of(RoleEnum.DOCTOR));
        when(userRepository.findById(5)).thenReturn(Optional.of(doctor));
        when(exceptionRepository.saveAndFlush(any(DoctorScheduleException.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Appointment existing = activeAppointment();
        when(appointmentRepository.findByDoctor_IdUserAndAppointmentDateBetweenAndStatusIn(eq(5), any(), any(), any()))
                .thenReturn(List.of(existing));

        var result = controller.closeDoctorDates(new AdminSchedulingController.ScheduleClosureRequest(
                5, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2),
                DoctorScheduleException.ExceptionType.VACATION, "Отпуск"));

        assertEquals(1, result.affectedAppointments());
        assertTrue(existing.isRequiresReschedule());
        verify(appointmentRepository).saveAll(anyList());
        verify(appointmentService).lockDoctorScheduleChange(5);
    }

    @Test
    void invalidClosureRangeIsRejected() {
        authenticate("ADMIN");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.closeDoctorDates(new AdminSchedulingController.ScheduleClosureRequest(
                        5, LocalDate.now().plusDays(2), LocalDate.now().plusDays(1),
                        DoctorScheduleException.ExceptionType.SICK_LEAVE, null)));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void adminRescheduleClearsTheTransferFlag() {
        authenticate("ADMIN");
        Appointment appointment = activeAppointment();
        appointment.setIdAppointment(31);
        appointment.setRequiresReschedule(true);
        when(appointmentRepository.findById(31)).thenReturn(Optional.of(appointment));
        when(appointmentService.saveAdminRescheduled(any())).thenAnswer(invocation -> {
            Appointment saved = invocation.getArgument(0);
            saved.setRequiresReschedule(false);
            return saved;
        });

        Appointment updated = controller.reschedule(31,
                new AdminSchedulingController.RescheduleRequest(LocalDateTime.now().plusDays(4)));

        assertFalse(updated.isRequiresReschedule());
        verify(appointmentService).saveAdminRescheduled(appointment);
    }

    private void authenticate(String role) {
        var authentication = new UsernamePasswordAuthenticationToken("test", "",
                List.of(new SimpleGrantedAuthority(role)));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Appointment activeAppointment() {
        Appointment appointment = new Appointment();
        appointment.setStatus("confirmed");
        appointment.setAppointmentDate(LocalDateTime.now().plusDays(1));
        return appointment;
    }
}
