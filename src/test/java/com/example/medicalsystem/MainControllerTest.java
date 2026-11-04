package com.example.medicalsystem;

import com.example.medicalsystem.controller.MainController;
import com.example.medicalsystem.service.AppointmentService;
import com.example.medicalsystem.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.data.domain.PageImpl;
import com.example.medicalsystem.model.Appointment;

import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MainControllerTest {

    @Test
    void adminHomeUsesSeparateDashboardWithClinicStats() {
        AppointmentService appointments = mock(AppointmentService.class);
        UserService users = mock(UserService.class);
        when(appointments.findByDateRange(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(appointments.findUpcomingScheduled(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(users.findAll()).thenReturn(List.of());
        MainController controller = new MainController(appointments, users);
        User admin = new User("admin", "password", List.of(new SimpleGrantedAuthority("ADMIN")));
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("admin_dashboard", controller.index(model, admin));
        assertEquals(0L, model.get("patientCount"));
        assertEquals(0L, model.get("doctorCount"));
        assertEquals(0, model.get("appointmentsToday"));
        assertEquals(List.of(), model.get("upcomingAppointments"));
    }

    @Test
    void patientHomeUsesPatientDashboardAndIncludesOwnAppointments() {
        AppointmentService appointments = mock(AppointmentService.class);
        UserService users = mock(UserService.class);
        com.example.medicalsystem.model.User patient = new com.example.medicalsystem.model.User();
        patient.setIdUser(4);
        patient.setFirstName("Анна");
        when(users.findByUsername("patient")).thenReturn(patient);
        when(appointments.findByUser(4)).thenReturn(List.of());
        MainController controller = new MainController(appointments, users);
        ExtendedModelMap model = new ExtendedModelMap();
        User principal = new User("patient", "password", List.of(new SimpleGrantedAuthority("PATIENT")));

        assertEquals("patient_dashboard", controller.index(model, principal));
        assertEquals(patient, model.get("patient"));
        assertEquals(List.of(), model.get("patientAppointments"));
    }

    @Test
    void doctorHomeIncludesOnlyUpcomingAppointmentsForThatDoctor() {
        AppointmentService appointments = mock(AppointmentService.class);
        UserService users = mock(UserService.class);
        com.example.medicalsystem.model.User doctor = new com.example.medicalsystem.model.User();
        doctor.setIdUser(8);
        doctor.setFirstName("Рамиз");
        when(users.findByUsername("doctor")).thenReturn(doctor);

        Appointment today = appointmentAt(LocalDate.now().atTime(11, 0));
        Appointment tomorrow = appointmentAt(LocalDate.now().plusDays(1).atTime(9, 0));
        Appointment outsideWeek = appointmentAt(LocalDate.now().plusDays(8).atTime(9, 0));
        Appointment withoutDate = appointmentAt(null);
        when(appointments.findByDoctor(8)).thenReturn(List.of(outsideWeek, tomorrow, withoutDate, today));

        MainController controller = new MainController(appointments, users);
        ExtendedModelMap model = new ExtendedModelMap();
        User principal = new User("doctor", "password", List.of(new SimpleGrantedAuthority("DOCTOR")));

        assertEquals("doctor_dashboard", controller.index(model, principal));
        assertEquals(List.of(today), model.get("todayAppointments"));
        assertEquals(List.of(today, tomorrow), model.get("weekAppointments"));
        assertEquals(1L, model.get("appointmentsToday"));
    }

    private Appointment appointmentAt(LocalDateTime date) {
        Appointment appointment = new Appointment();
        appointment.setAppointmentDate(date);
        return appointment;
    }
}
