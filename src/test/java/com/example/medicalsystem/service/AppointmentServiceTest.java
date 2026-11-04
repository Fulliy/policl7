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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

    @Test
    void upcomingScheduledAppointmentsAreSortedAndExcludeCancelledVisits() {
        AppointmentRepository repository = mock(AppointmentRepository.class);
        AppointmentService service = new AppointmentService(repository, mock(UserRepository.class),
                mock(DepartmentRepository.class));
        LocalDateTime now = LocalDateTime.now();
        Appointment later = scheduledAppointment(now.plusDays(3), "Запланирован");
        Appointment earlier = scheduledAppointment(now.plusDays(1), "запланирован");
        Appointment cancelled = scheduledAppointment(now.plusHours(2), "Отменен");
        when(repository.findByAppointmentDateAfter(now)).thenReturn(List.of(later, cancelled, earlier));

        assertEquals(List.of(earlier, later), service.findUpcomingScheduled(now));
    }

    @Test
    void cancellationPersistsStatusAndDoesNotDeleteAppointment() {
        AppointmentRepository repository = mock(AppointmentRepository.class);
        AppointmentService service = new AppointmentService(repository, mock(UserRepository.class),
                mock(DepartmentRepository.class));
        Appointment appointment = scheduledAppointment(LocalDateTime.now().plusDays(1), "Запланирован");
        appointment.setIdAppointment(12);
        when(repository.findById(12)).thenReturn(java.util.Optional.of(appointment));
        when(repository.save(appointment)).thenReturn(appointment);

        assertEquals("Отменен", service.cancel(12).getStatus());
        verify(repository).save(appointment);
    }

    @Test
    void completedAppointmentCannotBeCancelled() {
        AppointmentRepository repository = mock(AppointmentRepository.class);
        AppointmentService service = new AppointmentService(repository, mock(UserRepository.class),
                mock(DepartmentRepository.class));
        Appointment appointment = scheduledAppointment(LocalDateTime.now().minusDays(1), "Завершен");
        when(repository.findById(13)).thenReturn(java.util.Optional.of(appointment));

        assertThrows(IllegalStateException.class, () -> service.cancel(13));
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

    private Appointment scheduledAppointment(LocalDateTime date, String status) {
        Appointment appointment = new Appointment();
        appointment.setAppointmentDate(date);
        appointment.setStatus(status);
        return appointment;
    }
}
