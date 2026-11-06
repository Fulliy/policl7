package com.example.medicalsystem.service;

import com.example.medicalsystem.model.DoctorScheduleBreak;
import com.example.medicalsystem.model.DoctorWorkingHours;
import com.example.medicalsystem.model.RoleEnum;
import com.example.medicalsystem.model.User;
import com.example.medicalsystem.repository.DoctorScheduleBreakRepository;
import com.example.medicalsystem.repository.DoctorWorkingHoursRepository;
import com.example.medicalsystem.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

/** Seeds a recurring weekday schedule for local demo doctors that have no configured hours. */
@Component
@Profile("dev")
public class DoctorScheduleDemoDataSeeder implements CommandLineRunner {
    private final UserRepository users;
    private final DoctorWorkingHoursRepository workingHours;
    private final DoctorScheduleBreakRepository breaks;
    private final boolean enabled;

    public DoctorScheduleDemoDataSeeder(UserRepository users,
                                        DoctorWorkingHoursRepository workingHours,
                                        DoctorScheduleBreakRepository breaks,
                                        @Value("${app.demo-data.enabled:false}") boolean enabled) {
        this.users = users;
        this.workingHours = workingHours;
        this.breaks = breaks;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) return;

        List<User> doctors = users.findAll().stream()
                .filter(user -> user.getRoles() != null && user.getRoles().contains(RoleEnum.DOCTOR))
                .toList();
        for (User doctor : doctors) {
            boolean hasConfiguredHours = false;
            for (int day = 1; day <= 7; day++) {
                if (!workingHours.findByDoctor_IdUserAndDayOfWeek(doctor.getIdUser(), day).isEmpty()) {
                    hasConfiguredHours = true;
                    break;
                }
            }
            if (hasConfiguredHours) continue;

            for (int weekday = 1; weekday <= 5; weekday++) {
                DoctorWorkingHours shift = new DoctorWorkingHours();
                shift.setDoctor(doctor);
                shift.setDayOfWeek(weekday);
                shift.setStartTime(LocalTime.of(9, 0));
                shift.setEndTime(LocalTime.of(17, 0));
                shift = workingHours.save(shift);

                DoctorScheduleBreak lunch = new DoctorScheduleBreak();
                lunch.setWorkingHours(shift);
                lunch.setStartTime(LocalTime.of(13, 0));
                lunch.setEndTime(LocalTime.of(14, 0));
                breaks.save(lunch);
            }
        }
    }
}
