package com.example.medicalsystem.controller;

import com.example.medicalsystem.model.Appointment;
import com.example.medicalsystem.model.User;
import com.example.medicalsystem.model.AppointmentStatus;
import com.example.medicalsystem.service.AppointmentService;
import com.example.medicalsystem.service.MedicalRecordService;
import com.example.medicalsystem.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DoctorPatientsControllerTest {
    @Test
    void doctorCanOnlyOpenCardsOfPatientsTheyHaveSeen() {
        AppointmentService appointments = mock(AppointmentService.class);
        MedicalRecordService records = mock(MedicalRecordService.class);
        UserService users = mock(UserService.class);
        User doctor = new User(); doctor.setIdUser(5);
        User patient = new User(); patient.setIdUser(8);
        when(users.findByUsername("doctor")).thenReturn(doctor);
        Appointment visit = new Appointment(); visit.setUser(patient); visit.setStatus(AppointmentStatus.completed.name());
        visit.setAppointmentDate(java.time.LocalDateTime.now().minusDays(1));
        when(appointments.findByDoctor(5)).thenReturn(List.of(visit));
        DoctorPatientsController controller = new DoctorPatientsController(appointments, records, users);
        var principal = new org.springframework.security.core.userdetails.User("doctor", "", List.of());

        var model = new ConcurrentModel();
        assertEquals("doctorPatients", controller.list(principal, model));
        assertEquals(1, ((java.util.Collection<?>) model.getAttribute("patients")).size());
        assertThrows(ResponseStatusException.class, () -> controller.patientCard(99, principal, new ConcurrentModel()));
        var cardModel = new ConcurrentModel();
        when(appointments.findByUser(8)).thenReturn(List.of(visit));
        assertEquals("doctorPatientCard", controller.patientCard(8, principal, cardModel));
        assertEquals(List.of(), cardModel.getAttribute("medicalRecords"));
        verify(records).findAllForPatient(8);
    }
}
