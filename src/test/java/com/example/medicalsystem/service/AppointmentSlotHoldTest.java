package com.example.medicalsystem.service;

import com.example.medicalsystem.config.SchedulingProperties;
import com.example.medicalsystem.model.*;
import com.example.medicalsystem.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppointmentSlotHoldTest {
    @Test
    void reservationExpiresAfterConfiguredTenMinutesAndIsReturnedForTimer() {
        Fixture fixture = new Fixture();
        LocalDateTime before = LocalDateTime.now();

        AppointmentService.HoldResult hold = fixture.service.reserveSlot(8, 5, fixture.date.atTime(10, 0));

        assertNotNull(hold.expiresAt());
        assertFalse(hold.expiresAt().isBefore(before.plusMinutes(9)));
        assertFalse(hold.expiresAt().isAfter(before.plusMinutes(11)));
    }

    @Test
    void expiredReservationCannotBeConfirmedAndReturnsConflict() {
        Fixture fixture = new Fixture();
        AppointmentSlotHold expired = fixture.hold(71, 8, fixture.date.atTime(10, 0), LocalDateTime.now().minusSeconds(1));
        when(fixture.holds.findByIdAndPatient_IdUser(71, 8)).thenReturn(Optional.of(expired));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> fixture.service.saveBooking(fixture.booking(8, fixture.date.atTime(10, 0)), 71, false, false));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertTrue(error.getReason().contains("истекло"));
        verify(fixture.appointments, never()).saveAndFlush(any());
    }

    @Test
    void aNewReservationReplacesThePatientsPreviousHold() {
        Fixture fixture = new Fixture();
        LocalDateTime firstTime = fixture.date.atTime(10, 0);
        AppointmentSlotHold previous = fixture.hold(70, 8, firstTime, LocalDateTime.now().plusMinutes(5));
        when(fixture.holds.findByDoctor_IdUserAndExpiresAtAfter(eq(5), any())).thenReturn(List.of(previous));
        AtomicInteger ids = new AtomicInteger(80);
        doAnswer(invocation -> {
            AppointmentSlotHold saved = invocation.getArgument(0);
            saved.setId(ids.getAndIncrement());
            return saved;
        }).when(fixture.holds).saveAndFlush(any());

        var replaced = fixture.service.reserveSlot(8, 5, fixture.date.plusWeeks(1).atTime(10, 0));

        assertEquals(80, replaced.holdId());
        verify(fixture.holds).deleteByPatient_IdUser(8);
        verify(fixture.holds).saveAndFlush(any());
    }

    @Test
    void activeHoldMakesSlotUnavailableAndExpiredHoldsAreCleanedOnAvailabilityRead() {
        Fixture fixture = new Fixture();
        AppointmentSlotHold active = fixture.hold(72, 8, fixture.date.atTime(10, 0), LocalDateTime.now().plusMinutes(4));
        when(fixture.holds.findByDoctor_IdUserAndExpiresAtAfter(eq(5), any())).thenReturn(List.of(active));

        var availability = fixture.service.availability(5, fixture.date);

        assertFalse(availability.slots().stream().filter(slot -> slot.time().equals("10:00"))
                .findFirst().orElseThrow().available());
        verify(fixture.holds).deleteExpired(any());
    }

    @Test
    void administratorCanCreateMarkedAppointmentBeyondPatientBookingHorizon() {
        Fixture fixture = new Fixture();
        Appointment appointment = fixture.booking(8, fixture.date.plusDays(40).atTime(10, 0));
        appointment.setStatus("confirmed");

        Appointment saved = fixture.service.saveBooking(appointment, null, true, true);

        assertTrue(saved.isCreatedByAdmin());
        assertEquals("confirmed", saved.getStatus());
    }

    @Test
    void administratorCannotBookAnOccupiedSlot() {
        Fixture fixture = new Fixture();
        LocalDateTime start = fixture.date.atTime(10, 0);
        Appointment occupied = fixture.booking(19, start);
        occupied.setIdAppointment(101);
        when(fixture.appointments.findByDoctor_IdUserAndAppointmentDateBetween(eq(5), any(), any()))
                .thenReturn(List.of(occupied));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> fixture.service.saveBooking(fixture.booking(8, start), null, true, true));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void administratorRescheduleClearsFlagAndUsesNormalSlotConflictChecks() {
        Fixture fixture = new Fixture();
        LocalDateTime start = fixture.date.atTime(10, 0);
        Appointment appointment = fixture.booking(8, start);
        appointment.setIdAppointment(102);
        appointment.setRequiresReschedule(true);
        when(fixture.appointments.findById(102)).thenReturn(Optional.of(appointment));

        Appointment moved = fixture.service.saveAdminRescheduled(appointment);

        assertFalse(moved.isRequiresReschedule());
        assertFalse(moved.isCreatedByAdmin());
        verify(fixture.appointments).saveAndFlush(appointment);
    }

    private static final class Fixture {
        final LocalDate date = LocalDate.now().plusDays(2);
        final AppointmentRepository appointments = mock(AppointmentRepository.class);
        final AppointmentSlotHoldRepository holds = mock(AppointmentSlotHoldRepository.class);
        final UserRepository users = mock(UserRepository.class);
        final DoctorWorkingHoursRepository shifts = mock(DoctorWorkingHoursRepository.class);
        final DoctorScheduleBreakRepository breaks = mock(DoctorScheduleBreakRepository.class);
        final DoctorScheduleExceptionRepository exceptions = mock(DoctorScheduleExceptionRepository.class);
        final PatientBookingRestrictionRepository restrictions = mock(PatientBookingRestrictionRepository.class);
        final AppointmentService service;

        Fixture() {
            User patient = user(8), doctor = user(5);
            when(users.findById(8)).thenReturn(Optional.of(patient));
            when(users.findById(5)).thenReturn(Optional.of(doctor));
            when(shifts.findByDoctor_IdUserAndDayOfWeek(eq(5), anyInt())).thenAnswer(invocation -> {
                DoctorWorkingHours shift = new DoctorWorkingHours();
                shift.setId(1);
                shift.setDayOfWeek(invocation.getArgument(1));
                shift.setStartTime(LocalTime.of(9, 0));
                shift.setEndTime(LocalTime.of(17, 0));
                return List.of(shift);
            });
            when(breaks.findByWorkingHours_Id(any())).thenReturn(List.of());
            when(exceptions.findByDoctor_IdUserAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                    anyInt(), any(), any())).thenReturn(List.of());
            when(restrictions.findById(anyInt())).thenReturn(Optional.empty());
            when(appointments.findByUser_IdUser(anyInt())).thenReturn(List.of());
            when(appointments.findByDoctor_IdUserAndAppointmentDateBetween(anyInt(), any(), any())).thenReturn(List.of());
            when(appointments.findByUser_IdUserAndAppointmentDate(anyInt(), any())).thenReturn(List.of());
            when(holds.findByDoctor_IdUserAndExpiresAtAfter(anyInt(), any())).thenReturn(List.of());
            AtomicInteger ids = new AtomicInteger(40);
            when(holds.saveAndFlush(any())).thenAnswer(invocation -> {
                AppointmentSlotHold saved = invocation.getArgument(0);
                saved.setId(ids.getAndIncrement());
                return saved;
            });
            when(appointments.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
            service = new AppointmentService(appointments, users, mock(DepartmentRepository.class), shifts, breaks,
                    exceptions, restrictions, new SchedulingProperties(), mock(JdbcTemplate.class), holds);
        }

        Appointment booking(Integer patientId, LocalDateTime dateTime) {
            Appointment appointment = new Appointment();
            appointment.setUser(user(patientId));
            appointment.setDoctor(user(5));
            appointment.setAppointmentDate(dateTime);
            appointment.setStatus("pending");
            appointment.setType(AppointmentType.DOCTOR_VISIT);
            return appointment;
        }

        AppointmentSlotHold hold(Integer id, Integer patientId, LocalDateTime dateTime, LocalDateTime expiry) {
            AppointmentSlotHold hold = new AppointmentSlotHold();
            hold.setId(id);
            hold.setPatient(user(patientId));
            hold.setDoctor(user(5));
            hold.setAppointmentDate(dateTime);
            hold.setDurationMinutes(30);
            hold.setExpiresAt(expiry);
            return hold;
        }

        private static User user(Integer id) {
            User user = new User();
            user.setIdUser(id);
            user.setRoles(EnumSet.of(id == 5 ? RoleEnum.DOCTOR : RoleEnum.PATIENT));
            return user;
        }
    }
}
