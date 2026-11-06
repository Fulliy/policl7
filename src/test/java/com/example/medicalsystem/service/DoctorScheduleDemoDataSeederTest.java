package com.example.medicalsystem.service;

import com.example.medicalsystem.model.*;
import com.example.medicalsystem.repository.*;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DoctorScheduleDemoDataSeederTest {
    @Test
    void seedsWeekdayHoursAndLunchBreakWhenDoctorHasNoSchedule() {
        UserRepository users = mock(UserRepository.class);
        DoctorWorkingHoursRepository hours = mock(DoctorWorkingHoursRepository.class);
        DoctorScheduleBreakRepository breaks = mock(DoctorScheduleBreakRepository.class);
        User doctor = doctor();
        when(users.findAll()).thenReturn(List.of(doctor));
        when(hours.findByDoctor_IdUserAndDayOfWeek(eq(5), anyInt())).thenReturn(List.of());
        when(hours.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(breaks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new DoctorScheduleDemoDataSeeder(users, hours, breaks, true).run();

        verify(hours, times(5)).save(argThat(value -> value.getDayOfWeek() >= 1 && value.getDayOfWeek() <= 5
                && value.getStartTime().equals(LocalTime.of(9, 0)) && value.getEndTime().equals(LocalTime.of(17, 0))));
        verify(breaks, times(5)).save(argThat(value -> value.getStartTime().equals(LocalTime.of(13, 0))
                && value.getEndTime().equals(LocalTime.of(14, 0))));
    }

    @Test
    void doesNotOverwriteAnyExistingDoctorHours() {
        UserRepository users = mock(UserRepository.class);
        DoctorWorkingHoursRepository hours = mock(DoctorWorkingHoursRepository.class);
        DoctorScheduleBreakRepository breaks = mock(DoctorScheduleBreakRepository.class);
        when(users.findAll()).thenReturn(List.of(doctor()));
        DoctorWorkingHours configured = new DoctorWorkingHours();
        configured.setDayOfWeek(3);
        when(hours.findByDoctor_IdUserAndDayOfWeek(5, 3)).thenReturn(List.of(configured));

        new DoctorScheduleDemoDataSeeder(users, hours, breaks, true).run();

        verify(hours, never()).save(any());
        verifyNoInteractions(breaks);
    }

    @Test
    void doesNotSeedWhenDemoDataIsDisabled() {
        UserRepository users = mock(UserRepository.class);
        DoctorWorkingHoursRepository hours = mock(DoctorWorkingHoursRepository.class);
        DoctorScheduleBreakRepository breaks = mock(DoctorScheduleBreakRepository.class);

        new DoctorScheduleDemoDataSeeder(users, hours, breaks, false).run();

        verifyNoInteractions(users, hours, breaks);
    }

    private static User doctor() {
        User doctor = new User();
        doctor.setIdUser(5);
        doctor.setRoles(EnumSet.of(RoleEnum.DOCTOR));
        return doctor;
    }
}
