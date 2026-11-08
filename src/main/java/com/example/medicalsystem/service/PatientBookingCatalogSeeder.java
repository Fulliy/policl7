package com.example.medicalsystem.service;

import com.example.medicalsystem.model.*;
import com.example.medicalsystem.repository.AppointmentGoalRepository;
import com.example.medicalsystem.repository.ClinicBranchRepository;
import com.example.medicalsystem.repository.SpecialtyRepository;
import com.example.medicalsystem.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;

@Component
@Profile("dev")
public class PatientBookingCatalogSeeder implements CommandLineRunner {
    private final AppointmentGoalRepository goals;
    private final ClinicBranchRepository branches;
    private final SpecialtyRepository specialties;
    private final UserRepository users;
    private final boolean enabled;

    public PatientBookingCatalogSeeder(AppointmentGoalRepository goals, ClinicBranchRepository branches,
                                       SpecialtyRepository specialties, UserRepository users,
                                       @Value("${app.demo-data.enabled:false}") boolean enabled) {
        this.goals = goals; this.branches = branches; this.specialties = specialties; this.users = users;
        this.enabled = enabled;
    }

    @Override @Transactional
    public void run(String... args) {
        if (!enabled) return;
        ClinicBranch main = branch("Медцентр «Забота»", "г. Москва, ул. Заботы, 1", "+7 (495) 000-00-01");
        ClinicBranch north = branch("Филиал «Забота — Северный»", "г. Москва, ул. Северная, 10", "+7 (495) 000-00-02");
        List<User> doctors = users.findAll().stream()
                .filter(user -> user.getRoles() != null && user.getRoles().contains(RoleEnum.DOCTOR)).toList();
        for (int i = 0; i < doctors.size(); i++) {
            User doctor = doctors.get(i);
            if (doctor.getBranches() == null || doctor.getBranches().isEmpty()) {
                doctor.setBranches(new ArrayList<>(i % 2 == 0 || doctors.size() == 1
                        ? List.of(main, north) : List.of(main)));
                users.save(doctor);
            }
        }
        if (!doctors.isEmpty() && doctors.stream().noneMatch(User::isOnDutyOrvi)) {
            User doctor = doctors.get(0);
            doctor.setOnDutyOrvi(true);
            users.save(doctor);
        }
        List<Specialty> allDoctorSpecialties = specialtiesForDoctors(doctors);
        seedGoal("Вакцинация, в т.ч. от гриппа", "vaccine", AppointmentGoal.GoalGroup.POLYCLINIC,
                AppointmentGoal.ScenarioType.DOCTORS, 10, false, null, null, allDoctorSpecialties);
        seedGoal("Симптомы простуды", "cold", AppointmentGoal.GoalGroup.POLYCLINIC,
                AppointmentGoal.ScenarioType.ORVI_DUTY, 20, false, null, null, allDoctorSpecialties);
        seedGoal("Плохое самочувствие или боль", "pain", AppointmentGoal.GoalGroup.POLYCLINIC,
                AppointmentGoal.ScenarioType.SPECIALTY, 30, false, null, null, allDoctorSpecialties);
        seedGoal("Оформление документов", "documents", AppointmentGoal.GoalGroup.POLYCLINIC,
                AppointmentGoal.ScenarioType.DOCTORS, 40, false, null, null, allDoctorSpecialties);
        seedGoal("Выписка рецептов", "prescription", AppointmentGoal.GoalGroup.POLYCLINIC,
                AppointmentGoal.ScenarioType.DOCTORS, 50, false, null, null, allDoctorSpecialties);
        seedGoal("Диспансеризация", "checkup", AppointmentGoal.GoalGroup.POLYCLINIC,
                AppointmentGoal.ScenarioType.DOCTORS, 60, false, null, null, allDoctorSpecialties);
        seedGoal("Единое окно", "window", AppointmentGoal.GoalGroup.POLYCLINIC,
                AppointmentGoal.ScenarioType.DOCTORS, 70, false, null, null, allDoctorSpecialties);
        seedGoal("Запись в женскую консультацию", "women", AppointmentGoal.GoalGroup.OTHER_INSTITUTIONS,
                AppointmentGoal.ScenarioType.EXTERNAL, 80, false, "Запись выполняется через внешнюю женскую консультацию.", null, List.of());
        seedGoal("Запись к психологу", "psychologist", AppointmentGoal.GoalGroup.OTHER_INSTITUTIONS,
                AppointmentGoal.ScenarioType.EXTERNAL, 90, false, "Для записи обратитесь во внешнюю организацию.", null, List.of());
        seedGoal("Запись в центр дерматовенерологии", "skin", AppointmentGoal.GoalGroup.OTHER_INSTITUTIONS,
                AppointmentGoal.ScenarioType.EXTERNAL, 100, false, "Для записи обратитесь во внешний центр дерматовенерологии.", null, List.of());
    }

    private ClinicBranch branch(String name, String address, String phone) {
        return branches.findAllByOrderByNameAsc().stream().filter(item -> item.getName().equalsIgnoreCase(name))
                .findFirst().orElseGet(() -> { ClinicBranch item = new ClinicBranch(); item.setName(name); item.setAddress(address); item.setPhone(phone); return branches.save(item); });
    }

    private List<Specialty> specialtiesMatching(String... fragments) {
        return specialties.findAll().stream().filter(item -> item.getSpecialtyName() != null)
                .filter(item -> { String name = item.getSpecialtyName().toLowerCase(Locale.ROOT); return List.of(fragments).stream().anyMatch(name::contains); })
                .toList();
    }

    private List<Specialty> specialtiesForDoctors(List<User> doctors) {
        List<Specialty> result = doctors.stream().map(User::getSpecialty)
                .filter(java.util.Objects::nonNull).distinct().collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (result.isEmpty()) return specialties.findAll();
        return result;
    }

    private void seedGoal(String name, String icon, AppointmentGoal.GoalGroup group,
                          AppointmentGoal.ScenarioType scenario, int order, boolean paid,
                          String description, String url, List<Specialty> eligibleSpecialties) {
        AppointmentGoal goal = goals.findFirstByNameIgnoreCase(name).orElseGet(AppointmentGoal::new);
        goal.setName(name); goal.setIconKey(icon);
        goal.setGoalGroup(group); goal.setScenarioType(scenario); goal.setSortOrder(order);
        goal.setPaid(paid); goal.setDescription(description); goal.setExternalUrl(url);
        // Каталог целей должен сразу видеть новых врачей и их специальности.
        // Поэтому связь обновляется при каждом запуске, а не только для новой цели.
        goal.setSpecialties(new ArrayList<>(eligibleSpecialties));
        goals.save(goal);
    }
}
