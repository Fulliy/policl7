package com.example.medicalsystem.service;

import com.example.medicalsystem.model.Appointment;
import com.example.medicalsystem.model.Specialty;
import com.example.medicalsystem.model.User;
import com.example.medicalsystem.repository.AppointmentRepository;
import com.example.medicalsystem.repository.DepartmentRepository;
import com.example.medicalsystem.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppointmentServiceTest {

    @Test
    void patientCanSearchOwnAppointmentsByDoctorNameOrSpecialty() {
        AppointmentRepository repository = mock(AppointmentRepository.class);
        AppointmentService service = new AppointmentService(repository, mock(UserRepository.class),
                mock(DepartmentRepository.class));
        Appointment nameMatch = appointmentWithDoctor("Иванов", "Иван", "Терапевт");
        Appointment specialtyMatch = appointmentWithDoctor("Петров", "Пётр", "Кардиолог");
        Appointment other = appointmentWithDoctor("Сидоров", "Сергей", "Хирург");
        when(repository.findByUser_IdUser(7)).thenReturn(List.of(nameMatch, specialtyMatch, other));

        assertEquals(List.of(nameMatch), service.searchForUser(7, "doctor", "Иванов", 1).getContent());
        assertEquals(List.of(specialtyMatch), service.searchForUser(7, "doctor", "Кардиолог", 1).getContent());
    }

    @Test
    void patientAppointmentPeriodsAreFilteredBeforePagination() {
        AppointmentRepository repository = mock(AppointmentRepository.class);
        AppointmentService service = new AppointmentService(repository, mock(UserRepository.class),
                mock(DepartmentRepository.class));
        Appointment past = new Appointment();
        past.setAppointmentDate(LocalDateTime.now().minusDays(1));
        Appointment upcoming = new Appointment();
        upcoming.setAppointmentDate(LocalDateTime.now().plusDays(1));
        when(repository.findByUser_IdUser(7)).thenReturn(List.of(past, upcoming));

        assertEquals(List.of(past), service.searchForUser(7, null, null, 1, "past").getContent());
        assertEquals(List.of(upcoming), service.searchForUser(7, null, null, 1, "upcoming").getContent());
    }

    private Appointment appointmentWithDoctor(String surname, String firstName, String specialtyName) {
        User doctor = new User();
        doctor.setSurname(surname);
        doctor.setFirstName(firstName);
        doctor.setSpecialty(new Specialty(1, specialtyName, null));
        Appointment appointment = new Appointment();
        appointment.setDoctor(doctor);
        return appointment;
    }
}
