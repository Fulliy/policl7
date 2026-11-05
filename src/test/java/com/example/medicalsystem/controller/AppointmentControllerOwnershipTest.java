package com.example.medicalsystem.controller;

import com.example.medicalsystem.model.Appointment;
import com.example.medicalsystem.model.User;
import com.example.medicalsystem.service.AppointmentService;
import com.example.medicalsystem.service.DepartmentService;
import com.example.medicalsystem.service.ServiceEntityService;
import com.example.medicalsystem.service.UserService;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppointmentControllerOwnershipTest {
    @Test
    void patientListIsQueriedOnlyForTheAuthenticatedPatient() {
        AppointmentService appointments = mock(AppointmentService.class);
        UserService users = mock(UserService.class);
        User currentPatient = patient(18);
        Appointment ownAppointment = new Appointment();
        ownAppointment.setUser(currentPatient);
        when(users.findByUsername("patient-login")).thenReturn(currentPatient);
        when(appointments.searchForUser(18, null, null, 1, "upcoming"))
                .thenReturn(new PageImpl<>(List.of(ownAppointment)));
        AppointmentController controller = controller(appointments, users);

        String view = controller.list(1, null, null, "upcoming", null, new ExtendedModelMap(),
                principal("patient-login"));

        assertEquals("appointments", view);
        verify(appointments).searchForUser(18, null, null, 1, "upcoming");
        verify(appointments, never()).searchAdmin(any(), any(), any(), anyInt());
    }

    @Test
    void patientCannotOpenAnotherPatientsAppointmentForEditing() {
        AppointmentService appointments = mock(AppointmentService.class);
        UserService users = mock(UserService.class);
        User currentPatient = patient(18);
        User anotherPatient = patient(19);
        Appointment foreignAppointment = new Appointment();
        foreignAppointment.setUser(anotherPatient);
        when(users.findByUsername("patient-login")).thenReturn(currentPatient);
        when(appointments.findById(55)).thenReturn(foreignAppointment);
        AppointmentController controller = controller(appointments, users);

        assertThrows(AccessDeniedException.class,
                () -> controller.showEditForm(55, new ExtendedModelMap(), principal("patient-login")));
    }

    private AppointmentController controller(AppointmentService appointments, UserService users) {
        return new AppointmentController(appointments, users, mock(DepartmentService.class),
                mock(ServiceEntityService.class), mock(Validator.class));
    }

    private UserDetails principal(String username) {
        return new org.springframework.security.core.userdetails.User(username, "unused",
                List.of(new SimpleGrantedAuthority("PATIENT")));
    }

    private static User patient(Integer id) { User user = new User(); user.setIdUser(id); return user; }
}
