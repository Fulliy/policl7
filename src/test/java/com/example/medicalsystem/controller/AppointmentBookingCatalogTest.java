package com.example.medicalsystem.controller;

import com.example.medicalsystem.model.*;
import com.example.medicalsystem.repository.AppointmentGoalRepository;
import com.example.medicalsystem.repository.ClinicBranchRepository;
import com.example.medicalsystem.repository.SpecialtyRepository;
import com.example.medicalsystem.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AppointmentBookingCatalogTest {
    @Test
    void goalDoctorsEndpointReturnsAllAvailableDoctorsForInternalGoal() {
        AppointmentGoalRepository goals = mock(AppointmentGoalRepository.class);
        ClinicBranchRepository branches = mock(ClinicBranchRepository.class);
        SpecialtyRepository specialties = mock(SpecialtyRepository.class);
        UserService users = mock(UserService.class);
        AppointmentService appointments = mock(AppointmentService.class);
        User first = doctor(10, false);
        User second = doctor(11, false);
        when(users.findAll()).thenReturn(List.of(first, second));
        when(appointments.availabilityWithoutNearestDate(anyInt(), any()))
                .thenReturn(new AppointmentService.SlotAvailability(LocalDate.now(), true, List.of(), LocalDate.now()));
        AppointmentGoal goal = new AppointmentGoal();
        goal.setId(4); goal.setActive(true); goal.setScenarioType(AppointmentGoal.ScenarioType.SPECIALTY);
        when(goals.findById(4)).thenReturn(Optional.of(goal));
        AppointmentController controller = controller(goals, branches, specialties, users, appointments);

        assertEquals(List.of(10, 11), controller.doctorsForGoal(4).stream()
                .map(AppointmentController.BookingDoctor::id).toList());
    }

    @Test
    void externalGoalDoctorsEndpointReturnsEmptyList() {
        AppointmentGoalRepository goals = mock(AppointmentGoalRepository.class);
        ClinicBranchRepository branches = mock(ClinicBranchRepository.class);
        SpecialtyRepository specialties = mock(SpecialtyRepository.class);
        UserService users = mock(UserService.class);
        AppointmentService appointments = mock(AppointmentService.class);
        when(users.findAll()).thenReturn(List.of(doctor(10, true)));
        AppointmentGoal goal = new AppointmentGoal();
        goal.setId(5); goal.setActive(true); goal.setScenarioType(AppointmentGoal.ScenarioType.EXTERNAL);
        when(goals.findById(5)).thenReturn(Optional.of(goal));
        AppointmentController controller = controller(goals, branches, specialties, users, appointments);

        assertEquals(List.of(), controller.doctorsForGoal(5));
    }

    @Test
    void goalDoctorsEndpointFiltersOrviDoctorsAndReturnsBranch() {
        AppointmentGoalRepository goals = mock(AppointmentGoalRepository.class);
        ClinicBranchRepository branches = mock(ClinicBranchRepository.class);
        SpecialtyRepository specialties = mock(SpecialtyRepository.class);
        UserService users = mock(UserService.class);
        AppointmentService appointments = mock(AppointmentService.class);
        User orvi = doctor(10, true);
        User other = doctor(11, false);
        when(users.findAll()).thenReturn(List.of(orvi, other));
        when(appointments.availabilityWithoutNearestDate(anyInt(), any()))
                .thenReturn(new AppointmentService.SlotAvailability(LocalDate.now(), true, List.of(), LocalDate.now()));
        AppointmentGoal goal = new AppointmentGoal();
        goal.setId(3); goal.setActive(true); goal.setScenarioType(AppointmentGoal.ScenarioType.ORVI_DUTY);
        when(goals.findById(3)).thenReturn(Optional.of(goal));
        AppointmentController controller = controller(goals, branches, specialties, users, appointments);

        assertEquals(List.of(10), controller.doctorsForGoal(3).stream().map(AppointmentController.BookingDoctor::id).toList());
    }

    private AppointmentController controller(AppointmentGoalRepository goals, ClinicBranchRepository branches,
                                             SpecialtyRepository specialties, UserService users,
                                             AppointmentService appointments) {
        AppointmentController controller = new AppointmentController(appointments, users,
                mock(DepartmentService.class), mock(ServiceEntityService.class), mock(Validator.class));
        ReflectionTestUtils.setField(controller, "goalRepository", goals);
        ReflectionTestUtils.setField(controller, "branchRepository", branches);
        ReflectionTestUtils.setField(controller, "specialtyRepository", specialties);
        ReflectionTestUtils.setField(controller, "schedulingProperties", new com.example.medicalsystem.config.SchedulingProperties());
        return controller;
    }

    private User doctor(int id, boolean orvi) {
        User user = new User(); user.setIdUser(id); user.setRoles(new java.util.HashSet<>(List.of(RoleEnum.DOCTOR)));
        user.setOnDutyOrvi(orvi);
        ClinicBranch branch = new ClinicBranch(); branch.setId(id + 100); branch.setName("Филиал"); branch.setAddress("Адрес");
        user.setBranches(new ArrayList<>(List.of(branch)));
        return user;
    }
}
