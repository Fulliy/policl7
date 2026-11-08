package com.example.medicalsystem.service;

import com.example.medicalsystem.model.Appointment;
import com.example.medicalsystem.model.AppointmentType;
import com.example.medicalsystem.model.DoctorWorkingHours;
import com.example.medicalsystem.model.DoctorScheduleBreak;
import com.example.medicalsystem.model.DoctorScheduleException;
import com.example.medicalsystem.model.AppointmentStatus;
import com.example.medicalsystem.config.SchedulingProperties;
import com.example.medicalsystem.model.Specialty;
import com.example.medicalsystem.model.User;
import com.example.medicalsystem.repository.AppointmentRepository;
import com.example.medicalsystem.repository.DepartmentRepository;
import com.example.medicalsystem.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.List;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AppointmentServiceTest.TestApplication.class)
class AppointmentServiceTest {

    @Autowired
    private AppointmentService springAppointmentService;

    @Test
    void springBootContextCreatesSchedulingServiceWithoutConnectingToPatientDatabase() {
        assertNotNull(springAppointmentService);
    }

    @SpringBootConfiguration
    static class TestApplication {
        @Bean
        AppointmentService appointmentService() {
            return new AppointmentService(mock(AppointmentRepository.class), mock(UserRepository.class),
                    mock(DepartmentRepository.class), mock(com.example.medicalsystem.repository.DoctorWorkingHoursRepository.class),
                    mock(com.example.medicalsystem.repository.DoctorScheduleBreakRepository.class),
                    mock(com.example.medicalsystem.repository.DoctorScheduleExceptionRepository.class),
                    mock(com.example.medicalsystem.repository.PatientBookingRestrictionRepository.class),
                    new SchedulingProperties(), mock(JdbcTemplate.class));
        }
    }

    @Test
    void patientCanSearchOwnAppointmentsByDoctorNameOrSpecialty() {
        AppointmentRepository repository = mock(AppointmentRepository.class);
        AppointmentService service = service(repository);
        Appointment nameMatch = appointmentWithDoctor("Иванов", "Иван", "Терапевт");
        Appointment specialtyMatch = appointmentWithDoctor("Петров", "Пётр", "Кардиолог");
        Appointment other = appointmentWithDoctor("Сидоров", "Сергей", "Хирург");
        when(repository.findByUser_IdUser(7)).thenReturn(List.of(nameMatch, specialtyMatch, other));

        assertEquals(List.of(nameMatch), service.searchForUser(7, "doctor", "Иванов", 1).getContent());
        assertEquals(List.of(specialtyMatch), service.searchForUser(7, "doctor", "Кардиолог", 1).getContent());
    }

    @Test
    void patientAppointmentPeriodsFilterCancelledUpcomingVisits() {
        AppointmentRepository repository = mock(AppointmentRepository.class);
        AppointmentService service = service(repository);
        Appointment past = scheduledAppointment(LocalDateTime.now().minusDays(1), "completed");
        Appointment upcoming = scheduledAppointment(LocalDateTime.now().plusDays(1), "pending");
        Appointment cancelled = scheduledAppointment(LocalDateTime.now().plusDays(2), "cancelled");
        when(repository.findByUser_IdUser(7)).thenReturn(List.of(past, upcoming, cancelled));

        assertEquals(List.of(past, cancelled), service.searchForUser(7, null, null, 1, "past").getContent());
        assertEquals(List.of(upcoming), service.searchForUser(7, null, null, 1, "upcoming").getContent());
    }

    @Test
    void upcomingScheduledAppointmentsAreSortedAndExcludeCancelledVisits() {
        AppointmentRepository repository = mock(AppointmentRepository.class);
        AppointmentService service = service(repository);
        LocalDateTime now = LocalDateTime.now();
        Appointment later = scheduledAppointment(now.plusDays(3), "Запланирован");
        Appointment earlier = scheduledAppointment(now.plusDays(1), "запланирован");
        Appointment cancelled = scheduledAppointment(now.plusHours(2), "Отменен");
        when(repository.findByAppointmentDateAfter(now)).thenReturn(List.of(later, cancelled, earlier));

        assertEquals(List.of(earlier, later), service.findUpcomingScheduled(now));
    }

    @Test
    void patientCancellationDeletesAppointment() {
        AppointmentRepository repository = mock(AppointmentRepository.class);
        AppointmentService service = service(repository);
        Appointment appointment = scheduledAppointment(LocalDateTime.now().plusDays(1), "Запланирован");
        appointment.setIdAppointment(12);
        when(repository.findById(12)).thenReturn(java.util.Optional.of(appointment));

        assertEquals(null, service.cancelByPatient(12));
        verify(repository).delete(appointment);
        verify(repository).flush();
    }

    @Test
    void staffCancellationPersistsCancelledStatus() {
        AppointmentRepository repository = mock(AppointmentRepository.class);
        AppointmentService service = service(repository);
        Appointment appointment = scheduledAppointment(LocalDateTime.now().plusDays(1), "Запланирован");
        appointment.setIdAppointment(14);
        when(repository.findById(14)).thenReturn(java.util.Optional.of(appointment));
        when(repository.saveAndFlush(appointment)).thenReturn(appointment);

        assertEquals("cancelled", service.cancelByStaff(14).getStatus());
        verify(repository).saveAndFlush(appointment);
    }

    @Test
    void completedAppointmentCannotBeCancelled() {
        AppointmentRepository repository = mock(AppointmentRepository.class);
        AppointmentService service = service(repository);
        Appointment appointment = scheduledAppointment(LocalDateTime.now().minusDays(1), "Завершен");
        when(repository.findById(13)).thenReturn(java.util.Optional.of(appointment));

        assertThrows(ResponseStatusException.class, () -> service.cancelByPatient(13));
    }

    @Test
    void pastActiveAppointmentsDoNotConsumePatientLimits() {
        AppointmentRepository repository = mock(AppointmentRepository.class);
        AppointmentService service = service(repository);
        User patient = new User(); patient.setIdUser(7);
        Appointment old = scheduledAppointment(LocalDateTime.now().minusDays(1), "pending"); old.setUser(patient);
        List<Appointment> existing = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Appointment appointment = scheduledAppointment(LocalDateTime.now().minusDays(i + 1), "confirmed");
            appointment.setUser(patient); existing.add(appointment);
        }
        when(repository.findByUser_IdUser(7)).thenReturn(existing);
        Appointment candidate = booking(7, 5, LocalDate.now().plusDays(2).atTime(10, 0));
        assertFalse(existing.stream().anyMatch(a -> a.getAppointmentDate().isAfter(LocalDateTime.now())));
        when(repository.saveAndFlush(candidate)).thenReturn(candidate);
        service.saveBooking(candidate);
        verify(repository).saveAndFlush(candidate);
    }

    @Test
    void occupiedSlotIsUnavailableAndCreateReturnsConflict() {
        AppointmentRepository repository = mock(AppointmentRepository.class);
        AppointmentService service = service(repository);
        LocalDate day = nextWeekday();
        Appointment occupied = scheduledAppointment(day.atTime(10, 0), "Запланирован");
        occupied.setIdAppointment(77);
        User doctor = new User(); doctor.setIdUser(5); occupied.setDoctor(doctor);
        when(repository.findByDoctor_IdUserAndAppointmentDateBetween(eq(5), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(List.of(occupied));
        when(repository.findByDoctor_IdUser(5)).thenReturn(List.of(occupied));
        when(repository.findByUser_IdUser(8)).thenReturn(List.of());

        assertFalse(service.availability(5, day).slots().stream()
                .filter(slot -> slot.time().equals("10:00")).findFirst().orElseThrow().available());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.saveBooking(booking(8, 5, day.atTime(10, 0))));
        assertEquals(409, error.getStatusCode().value());
        assertEquals("Это время уже занято, выберите другое", error.getReason());
    }

    @Test
    void fullyBookedDayHasNoAvailableSlots() {
        AppointmentRepository repository = mock(AppointmentRepository.class);
        AppointmentService service = service(repository);
        LocalDate day = nextWeekday();
        List<Appointment> occupied = new ArrayList<>();
        for (LocalTime time = LocalTime.of(9, 0); time.isBefore(LocalTime.of(17, 0)); time = time.plusMinutes(30)) {
            occupied.add(scheduledAppointment(day.atTime(time), "Запланирован"));
        }
        when(repository.findByDoctor_IdUserAndAppointmentDateBetween(eq(5), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(occupied);

        var availability = service.availability(5, day);

        assertFalse(availability.available());
        assertTrue(availability.slots().stream().noneMatch(AppointmentService.SlotDto::available));
        assertTrue(availability.nearestDate() != null);
    }

    @Test
    void patientCannotBookOverlappingTimesWithAnotherDoctor() {
        AppointmentRepository repository = mock(AppointmentRepository.class);
        AppointmentService service = service(repository);
        LocalDate day = nextWeekday();
        Appointment existing = persistedBooking(90, 8, 5, day.atTime(10, 0));
        existing.setDurationMinutes(60);
        when(repository.findByDoctor_IdUser(6)).thenReturn(List.of());
        when(repository.findByUser_IdUser(8)).thenReturn(List.of(existing));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.saveBooking(booking(8, 6, day.atTime(10, 30))));

        assertEquals(409, error.getStatusCode().value());
        assertEquals("Это время уже занято, выберите другое", error.getReason());
    }

    @Test
    void cancellingAppointmentMakesItsSlotAvailableAgain() {
        AppointmentRepository repository = mock(AppointmentRepository.class);
        AppointmentService service = service(repository);
        LocalDate day = nextWeekday();
        Appointment occupied = booking(8, 5, day.atTime(10, 0));
        occupied.setIdAppointment(88);
        when(repository.findById(88)).thenReturn(java.util.Optional.of(occupied));
        when(repository.findByDoctor_IdUserAndAppointmentDateBetween(eq(5), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenAnswer(invocation -> List.of(occupied));
        when(repository.save(occupied)).thenAnswer(invocation -> occupied);

        service.cancel(88);

        assertTrue(service.availability(5, day).slots().stream()
                .filter(slot -> slot.time().equals("10:00")).findFirst().orElseThrow().available());
    }

    @Test
    void concurrentPatientsCannotPersistSameDoctorSlot() throws Exception {
        AppointmentRepository repository = mock(AppointmentRepository.class);
        AppointmentService service = service(repository);
        LocalDate day = nextWeekday(); LocalDateTime dateTime = day.atTime(11, 0);
        when(repository.findByDoctor_IdUser(5)).thenReturn(List.of());
        when(repository.findByUser_IdUserAndAppointmentDate(ArgumentMatchers.anyInt(), eq(dateTime))).thenReturn(List.of());
        var insertedSlots = ConcurrentHashMap.<String>newKeySet();
        when(repository.saveAndFlush(ArgumentMatchers.any())).thenAnswer(invocation -> {
            Appointment value = invocation.getArgument(0);
            if (!insertedSlots.add(value.getDoctor().getIdUser() + ":" + value.getAppointmentDate())) {
                throw new DataIntegrityViolationException("unique slot index");
            }
            return value;
        });
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(() -> attemptAfter(start, service, booking(20, 5, dateTime)));
            Future<Boolean> second = pool.submit(() -> attemptAfter(start, service, booking(21, 5, dateTime)));
            start.countDown();
            assertTrue(first.get() ^ second.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void rejectsBookingInsideMinimumNoticeAndBeyondHorizon() {
        AppointmentService service = service(mock(AppointmentRepository.class));
        LocalDateTime soon = LocalDateTime.now().plusMinutes(30);
        LocalDateTime tooFar = LocalDateTime.now().plusDays(31);

        assertThrows(ResponseStatusException.class, () -> service.saveBooking(booking(8, 5, soon)));
        assertThrows(ResponseStatusException.class, () -> service.saveBooking(booking(8, 5, tooFar)));
    }

    @Test
    void rejectsSlotThatIntersectsDoctorBreak() {
        AppointmentRepository repository = mock(AppointmentRepository.class);
        TestFixture fixture = fixture(repository);
        DoctorScheduleBreak pause = new DoctorScheduleBreak();
        pause.setStartTime(LocalTime.of(10, 0));
        pause.setEndTime(LocalTime.of(10, 30));
        when(fixture.breaks().findByWorkingHours_Id(1)).thenReturn(List.of(pause));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> fixture.service().saveBooking(booking(8, 5, nextWeekday().atTime(10, 0))));
        assertTrue(error.getReason().contains("перерыв"));
    }

    @Test
    void scheduleExceptionHidesCalendarAndRejectsBooking() {
        AppointmentRepository repository = mock(AppointmentRepository.class);
        TestFixture fixture = fixture(repository);
        DoctorScheduleException absence = new DoctorScheduleException();
        absence.setStartDate(nextWeekday());
        absence.setEndDate(nextWeekday());
        when(fixture.exceptions().findByDoctor_IdUserAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                eq(5), eq(nextWeekday()), eq(nextWeekday()))).thenReturn(List.of(absence));

        assertFalse(fixture.service().availability(5, nextWeekday()).available());
        assertThrows(ResponseStatusException.class,
                () -> fixture.service().saveBooking(booking(8, 5, nextWeekday().atTime(10, 0))));
    }

    @Test
    void enforcesActiveAppointmentLimitsPerDoctorAndOverall() {
        AppointmentRepository repository = mock(AppointmentRepository.class);
        AppointmentService service = service(repository);
        LocalDate day = nextWeekday();
        List<Appointment> records = List.of(
                persistedBooking(101, 8, 5, day.plusDays(1).atTime(9, 0)),
                persistedBooking(102, 8, 5, day.plusDays(2).atTime(9, 0)),
                persistedBooking(103, 8, 5, day.plusDays(3).atTime(9, 0)));
        when(repository.findByUser_IdUser(8)).thenReturn(records);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.saveBooking(booking(8, 5, day.atTime(10, 0))));
        assertTrue(error.getReason().contains("одному врачу"));

        List<Appointment> total = new ArrayList<>(records);
        total.add(persistedBooking(104, 8, 6, day.plusDays(4).atTime(9, 0)));
        total.add(persistedBooking(105, 8, 7, day.plusDays(5).atTime(9, 0)));
        when(repository.findByUser_IdUser(8)).thenReturn(total);
        error = assertThrows(ResponseStatusException.class,
                () -> service.saveBooking(booking(8, 6, day.atTime(10, 0))));
        assertTrue(error.getReason().contains("активных записей"));
    }

    @Test
    void blocksPatientAfterThreeRecentNoShows() {
        AppointmentRepository repository = mock(AppointmentRepository.class);
        AppointmentService service = service(repository);
        List<Appointment> noShows = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Appointment value = booking(8, 5, LocalDateTime.now().minusDays(i));
            value.setStatus("no_show");
            noShows.add(value);
        }
        when(repository.findByUser_IdUser(8)).thenReturn(noShows);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.saveBooking(booking(8, 5, nextWeekday().atTime(10, 0))));
        assertEquals("Запись возможна только через регистратуру", error.getReason());
    }

    @Test
    void cancellationAfterDeadlineIsRejectedAndTerminalStatusCannotReturnToConfirmed() {
        AppointmentRepository repository = mock(AppointmentRepository.class);
        AppointmentService service = service(repository);
        Appointment soon = scheduledAppointment(LocalDateTime.now().plusHours(1), "confirmed");
        when(repository.findById(40)).thenReturn(java.util.Optional.of(soon));
        ResponseStatusException deadline = assertThrows(ResponseStatusException.class, () -> service.cancelByPatient(40));
        assertEquals("Отмена возможна только по телефону регистратуры", deadline.getReason());

        Appointment cancelled = scheduledAppointment(nextWeekday().atTime(10, 0), "cancelled");
        cancelled.setIdAppointment(41);
        when(repository.findById(41)).thenReturn(java.util.Optional.of(cancelled));
        Appointment update = booking(8, 5, nextWeekday().atTime(10, 0));
        update.setIdAppointment(41);
        update.setStatus("confirmed");
        ResponseStatusException transition = assertThrows(ResponseStatusException.class,
                () -> service.saveBooking(update));
        assertEquals(409, transition.getStatusCode().value());
        assertFalse(AppointmentStatus.cancelled.canTransitionTo(AppointmentStatus.confirmed));
    }

    @Test
    void pastAppointmentCannotBeCancelledOrRescheduled() {
        AppointmentRepository repository = mock(AppointmentRepository.class);
        AppointmentService service = service(repository);
        Appointment past = scheduledAppointment(LocalDateTime.now().minusDays(1), "confirmed");
        past.setIdAppointment(49);
        when(repository.findById(49)).thenReturn(java.util.Optional.of(past));

        ResponseStatusException cancelError = assertThrows(ResponseStatusException.class, () -> service.cancelByPatient(49));
        assertEquals("Нельзя отменить прошедший приём.", cancelError.getReason());
        Appointment update = booking(8, 5, nextWeekday().atTime(10, 0));
        update.setIdAppointment(49);
        update.setStatus("confirmed");
        ResponseStatusException updateError = assertThrows(ResponseStatusException.class, () -> service.saveBooking(update));
        assertEquals("Нельзя изменить прошедший приём.", updateError.getReason());
    }

    private static boolean attemptAfter(CountDownLatch start, AppointmentService service, Appointment value) throws Exception {
        start.await();
        try { service.saveBooking(value); return true; }
        catch (ResponseStatusException exception) { assertEquals(409, exception.getStatusCode().value()); return false; }
    }

    private Appointment booking(Integer patientId, Integer doctorId, LocalDateTime dateTime) {
        User patient = new User(); patient.setIdUser(patientId);
        User doctor = new User(); doctor.setIdUser(doctorId);
        Appointment appointment = new Appointment(); appointment.setType(AppointmentType.DOCTOR_VISIT);
        appointment.setUser(patient); appointment.setDoctor(doctor); appointment.setAppointmentDate(dateTime);
        appointment.setStatus("pending"); return appointment;
    }

    private Appointment persistedBooking(Integer id, Integer patientId, Integer doctorId, LocalDateTime dateTime) {
        Appointment appointment = booking(patientId, doctorId, dateTime);
        appointment.setIdAppointment(id);
        return appointment;
    }

    private AppointmentService service(AppointmentRepository repository) {
        return fixture(repository).service();
    }

    private TestFixture fixture(AppointmentRepository repository) {
        var workingHours = mock(com.example.medicalsystem.repository.DoctorWorkingHoursRepository.class);
        var breaks = mock(com.example.medicalsystem.repository.DoctorScheduleBreakRepository.class);
        var exceptions = mock(com.example.medicalsystem.repository.DoctorScheduleExceptionRepository.class);
        var restrictions = mock(com.example.medicalsystem.repository.PatientBookingRestrictionRepository.class);
        DoctorWorkingHours shift = new DoctorWorkingHours();
        shift.setId(1);
        shift.setStartTime(LocalTime.of(9, 0));
        shift.setEndTime(LocalTime.of(17, 0));
        when(workingHours.findByDoctor_IdUserAndDayOfWeek(eq(5), ArgumentMatchers.anyInt()))
                .thenAnswer(invocation -> {
                    shift.setDayOfWeek(invocation.getArgument(1));
                    return List.of(shift);
                });
        when(breaks.findByWorkingHours_Id(ArgumentMatchers.any())).thenReturn(List.of());
        when(exceptions.findByDoctor_IdUserAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                ArgumentMatchers.anyInt(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(List.of());
        when(restrictions.findById(ArgumentMatchers.anyInt())).thenReturn(java.util.Optional.empty());
        AppointmentService service = new AppointmentService(repository, mock(UserRepository.class), mock(DepartmentRepository.class),
                workingHours, breaks, exceptions, restrictions, new SchedulingProperties(), mock(JdbcTemplate.class));
        return new TestFixture(service, breaks, exceptions);
    }

    private record TestFixture(AppointmentService service,
                               com.example.medicalsystem.repository.DoctorScheduleBreakRepository breaks,
                               com.example.medicalsystem.repository.DoctorScheduleExceptionRepository exceptions) { }

    private static LocalDate nextWeekday() {
        LocalDate date = LocalDate.now().plusDays(1);
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) date = date.plusDays(1);
        return date;
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
