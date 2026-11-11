package com.example.medicalsystem.service;

import com.example.medicalsystem.model.Appointment;
import com.example.medicalsystem.model.AppointmentGoal;
import com.example.medicalsystem.model.AppointmentType;
import com.example.medicalsystem.model.ClinicBranch;
import com.example.medicalsystem.model.Department;
import com.example.medicalsystem.model.DoctorScheduleBreak;
import com.example.medicalsystem.model.DoctorWorkingHours;
import com.example.medicalsystem.model.MedicalRecord;
import com.example.medicalsystem.model.MedicalServiceEntity;
import com.example.medicalsystem.model.PatientAffiliation;
import com.example.medicalsystem.model.Prescription;
import com.example.medicalsystem.model.Role;
import com.example.medicalsystem.model.RoleEnum;
import com.example.medicalsystem.model.Specialty;
import com.example.medicalsystem.model.User;
import com.example.medicalsystem.repository.AppointmentGoalRepository;
import com.example.medicalsystem.repository.AppointmentRepository;
import com.example.medicalsystem.repository.ClinicBranchRepository;
import com.example.medicalsystem.repository.DepartmentRepository;
import com.example.medicalsystem.repository.DoctorScheduleBreakRepository;
import com.example.medicalsystem.repository.DoctorWorkingHoursRepository;
import com.example.medicalsystem.repository.MedicalRecordRepository;
import com.example.medicalsystem.repository.PatientAffiliationRepository;
import com.example.medicalsystem.repository.PrescriptionRepository;
import com.example.medicalsystem.repository.RoleRepository;
import com.example.medicalsystem.repository.ServiceRepository;
import com.example.medicalsystem.repository.SpecialtyRepository;
import com.example.medicalsystem.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Rebuilds the local development database with a coherent, repeatable demo dataset.
 * It is deliberately disabled outside the dev profile and behind two explicit flags.
 */
@Component
@Profile("dev")
@Order(Ordered.LOWEST_PRECEDENCE)
public class DemoDataResetOrchestrator implements CommandLineRunner {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Europe/Moscow");

    private final JdbcTemplate jdbc;
    private final DepartmentRepository departments;
    private final SpecialtyRepository specialties;
    private final RoleRepository roles;
    private final ServiceRepository services;
    private final ClinicBranchRepository branches;
    private final AppointmentGoalRepository goals;
    private final UserRepository users;
    private final AppointmentRepository appointments;
    private final MedicalRecordRepository medicalRecords;
    private final PrescriptionRepository prescriptions;
    private final DoctorWorkingHoursRepository workingHours;
    private final DoctorScheduleBreakRepository scheduleBreaks;
    private final PatientAffiliationRepository affiliations;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final boolean reset;

    public DemoDataResetOrchestrator(
            JdbcTemplate jdbc,
            DepartmentRepository departments,
            SpecialtyRepository specialties,
            RoleRepository roles,
            ServiceRepository services,
            ClinicBranchRepository branches,
            AppointmentGoalRepository goals,
            UserRepository users,
            AppointmentRepository appointments,
            MedicalRecordRepository medicalRecords,
            PrescriptionRepository prescriptions,
            DoctorWorkingHoursRepository workingHours,
            DoctorScheduleBreakRepository scheduleBreaks,
            PatientAffiliationRepository affiliations,
            PasswordEncoder passwordEncoder,
            @Value("${app.demo-data.enabled:false}") boolean enabled,
            @Value("${app.demo-data.reset:false}") boolean reset) {
        this.jdbc = jdbc;
        this.departments = departments;
        this.specialties = specialties;
        this.roles = roles;
        this.services = services;
        this.branches = branches;
        this.goals = goals;
        this.users = users;
        this.appointments = appointments;
        this.medicalRecords = medicalRecords;
        this.prescriptions = prescriptions;
        this.workingHours = workingHours;
        this.scheduleBreaks = scheduleBreaks;
        this.affiliations = affiliations;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.reset = reset;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled || !reset) return;

        jdbc.execute("select pg_advisory_xact_lock(2147483000, 2147483000)");
        truncateDemoTables();

        List<Department> departmentList = seedDepartments();
        List<Specialty> specialtyList = seedSpecialties();
        seedBusinessRoles(specialtyList);
        List<MedicalServiceEntity> serviceList = seedServices();
        List<ClinicBranch> branchList = seedBranches();
        seedGoals(specialtyList);

        createUser("admin", "admin@clinic.local", "Иванова", "Марина", "Олеговна",
                RoleEnum.ADMIN, null, null, null, false);
        User primaryDoctor = createUser("doctor", "doctor@clinic.local", "Петров", "Алексей", "Сергеевич",
                RoleEnum.DOCTOR, departmentList.get(0), specialtyList.get(0), branchList, true);
        User primaryPatient = createUser("patient", "patient@clinic.local", "Сидорова", "Анна", "Алексеевна",
                RoleEnum.PATIENT, null, null, null, false);

        List<User> doctors = new ArrayList<>();
        doctors.add(primaryDoctor);
        for (int i = 1; i < specialtyList.size(); i++) {
            Specialty specialty = specialtyList.get(i);
            Department department = departmentList.get(i % departmentList.size());
            User doctor = createUser(
                    doctorUsername(specialty),
                    "doctor" + (i + 1) + "@clinic.local",
                    doctorSurname(i),
                    doctorFirstName(i),
                    doctorMiddleName(i),
                    RoleEnum.DOCTOR,
                    department,
                    specialty,
                    branchList,
                    i == 1);
            doctors.add(doctor);
        }

        List<User> patients = new ArrayList<>();
        patients.add(primaryPatient);
        String[][] patientNames = {
                {"Кузнецова", "Елена", "Викторовна"}, {"Смирнов", "Дмитрий", "Андреевич"},
                {"Попова", "Ольга", "Николаевна"}, {"Васильев", "Максим", "Игоревич"},
                {"Новикова", "Ирина", "Павловна"}, {"Морозов", "Сергей", "Александрович"},
                {"Волкова", "Наталья", "Романовна"}, {"Фёдоров", "Артём", "Михайлович"},
                {"Лебедева", "Мария", "Владимировна"}, {"Козлов", "Илья", "Денисович"},
                {"Соколова", "Татьяна", "Алексеевна"}, {"Орлов", "Роман", "Евгеньевич"},
                {"Андреева", "Светлана", "Игоревна"}, {"Макаров", "Владислав", "Олегович"},
                {"Никитина", "Юлия", "Сергеевна"}, {"Захаров", "Николай", "Петрович"},
                {"Зайцева", "Вера", "Андреевна"}, {"Соловьёв", "Кирилл", "Викторович"},
                {"Борисова", "Алина", "Максимовна"}, {"Яковлев", "Павел", "Ильич"},
                {"Григорьева", "Людмила", "Олеговна"}, {"Романов", "Евгений", "Анатольевич"},
                {"Вишнякова", "Дарья", "Ивановна"}, {"Тихонов", "Михаил", "Валерьевич"},
                {"Комарова", "Анастасия", "Романовна"}, {"Ершов", "Денис", "Станиславович"},
                {"Калинина", "Марина", "Юрьевна"}, {"Беляев", "Александр", "Константинович"},
                {"Крылова", "Полина", "Дмитриевна"}, {"Мельников", "Виктор", "Игоревич"},
                {"Гусева", "Валентина", "Михайловна"}, {"Данилов", "Андрей", "Павлович"},
                {"Тарасова", "Кристина", "Алексеевна"}, {"Филиппов", "Олег", "Владимирович"},
                {"Маркова", "Екатерина", "Сергеевна"}, {"Куликов", "Степан", "Андреевич"}
        };
        for (int i = 0; i < patientNames.length; i++) {
            String[] name = patientNames[i];
            patients.add(createUser("patient" + String.format("%02d", i + 1),
                    "patient" + String.format("%02d", i + 1) + "@clinic.local",
                    name[0], name[1], name[2], RoleEnum.PATIENT,
                    null, null, null, false));
        }

        seedSchedules(doctors);
        seedAppointmentsAndRecords(doctors, patients, departmentList, branchList, serviceList);
        seedDoctorAlexeyTestVisits(primaryDoctor, patients, departmentList, branchList);
        seedAffiliations(patients, branchList);

        System.out.println("Demo database rebuilt: " + doctors.size() + " doctors and "
                + patients.size() + " patients.");
    }

    private void truncateDemoTables() {
        jdbc.execute("TRUNCATE TABLE "
                + "appointment_slot_holds, patient_booking_restrictions, patient_referrals, patient_affiliations, "
                + "doctor_schedule_breaks, doctor_working_hours, doctor_schedule_exceptions, "
                + "appointment_services, appointment_goal_specialties, doctor_branches, doctor_departments, user_role, "
                + "prescriptions, medical_records, appointments, users, roles, services, clinic_branches, "
                + "appointment_goals, departments, specialties RESTART IDENTITY");
    }

    private List<Department> seedDepartments() {
        String[] names = {"Терапевтическое отделение", "Кардиологическое отделение", "Неврологическое отделение",
                "Хирургическое отделение", "Офтальмологическое отделение", "ЛОР-отделение",
                "Урологическое отделение", "Дерматологическое отделение", "Гинекологическое отделение",
                "Педиатрическое отделение", "Травматологическое отделение", "Эндокринологическое отделение"};
        List<Department> result = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            Department department = new Department();
            department.setDepartmentName(names[i]);
            department.setFloor(i / 3 + 1);
            department.setRoomNumber(String.valueOf(101 + i));
            result.add(departments.save(department));
        }
        return result;
    }

    private List<Specialty> seedSpecialties() {
        String[][] values = {
                {"Терапия", "Диагностика и лечение распространённых заболеваний у взрослых."},
                {"Кардиология", "Профилактика и лечение заболеваний сердца и сосудов."},
                {"Неврология", "Заболевания нервной системы и восстановительное лечение."},
                {"Хирургия", "Плановая хирургическая помощь и консультации."},
                {"Офтальмология", "Диагностика и лечение заболеваний глаз."},
                {"Оториноларингология", "Заболевания уха, горла и носа."},
                {"Урология", "Заболевания мочевыделительной системы."},
                {"Дерматология", "Заболевания кожи и её придатков."},
                {"Гинекология", "Женское здоровье и профилактические осмотры."},
                {"Педиатрия", "Медицинская помощь детям и подросткам."},
                {"Травматология", "Травмы, заболевания суставов и опорно-двигательного аппарата."},
                {"Эндокринология", "Заболевания эндокринной системы и обмена веществ."}
        };
        List<Specialty> result = new ArrayList<>();
        for (String[] value : values) {
            Specialty specialty = new Specialty();
            specialty.setSpecialtyName(value[0]);
            specialty.setDescription(value[1]);
            result.add(specialties.save(specialty));
        }
        return result;
    }

    private void seedBusinessRoles(List<Specialty> specialtyList) {
        for (Specialty specialty : specialtyList) {
            Role role = new Role();
            role.setRoleName("Врач - " + specialty.getSpecialtyName());
            role.setDescription("Профиль врача специальности «" + specialty.getSpecialtyName() + "».");
            role.setSpecialty(specialty);
            roles.save(role);
        }
    }

    private List<MedicalServiceEntity> seedServices() {
        String[][] values = {
                {"Консультация терапевта", "1500", "30"}, {"ЭКГ", "1200", "30"},
                {"УЗИ брюшной полости", "2500", "45"}, {"Общий анализ крови", "800", "15"},
                {"Анализ мочи", "400", "15"}, {"Рентгенография", "1900", "30"},
                {"Профилактический осмотр", "1000", "30"}, {"Перевязка", "700", "20"}
        };
        List<MedicalServiceEntity> result = new ArrayList<>();
        for (String[] value : values) {
            MedicalServiceEntity service = new MedicalServiceEntity();
            service.setServiceName(value[0]);
            service.setCost(Integer.parseInt(value[1]));
            service.setDurationMinutes(Integer.parseInt(value[2]));
            service.setDescription(value[0]);
            service.setServiceType(MedicalServiceEntity.ServiceType.PRIMARY);
            result.add(services.save(service));
        }
        return result;
    }

    private List<ClinicBranch> seedBranches() {
        return List.of(saveBranch("Медцентр «Забота»", "г. Москва, ул. Заботы, 1", "+7 (495) 000-00-01"),
                saveBranch("Филиал «Забота — Северный»", "г. Москва, ул. Северная, 10", "+7 (495) 000-00-02"));
    }

    private ClinicBranch saveBranch(String name, String address, String phone) {
        ClinicBranch branch = new ClinicBranch();
        branch.setName(name);
        branch.setAddress(address);
        branch.setPhone(phone);
        return branches.save(branch);
    }

    private void seedGoals(List<Specialty> specialtyList) {
        createGoal("Вакцинация", "vaccine", AppointmentGoal.ScenarioType.DOCTORS,
                AppointmentGoal.GoalGroup.POLYCLINIC, 10, false, "Плановая вакцинация и профилактика.", specialtyList);
        createGoal("Симптомы простуды", "cold", AppointmentGoal.ScenarioType.ORVI_DUTY,
                AppointmentGoal.GoalGroup.POLYCLINIC, 20, false, "Помощь при симптомах простуды.", specialtyList);
        createGoal("Плохое самочувствие или боль", "pain", AppointmentGoal.ScenarioType.SPECIALTY,
                AppointmentGoal.GoalGroup.POLYCLINIC, 30, false, "Подберём профильного врача.", specialtyList);
        createGoal("Оформление документов", "documents", AppointmentGoal.ScenarioType.DOCTORS,
                AppointmentGoal.GoalGroup.POLYCLINIC, 40, false, "Справки и медицинские документы.", specialtyList);
        createGoal("Выписка рецепта", "prescription", AppointmentGoal.ScenarioType.DOCTORS,
                AppointmentGoal.GoalGroup.POLYCLINIC, 50, false, "Получение или продление рецепта.", specialtyList);
        createGoal("Диспансеризация", "checkup", AppointmentGoal.ScenarioType.DOCTORS,
                AppointmentGoal.GoalGroup.POLYCLINIC, 60, false, "Плановый профилактический осмотр.", specialtyList);
        createGoal("Платная консультация", "medical", AppointmentGoal.ScenarioType.PAID_SERVICE,
                AppointmentGoal.GoalGroup.POLYCLINIC, 70, true, "Консультация и дополнительные услуги.", specialtyList);
        createGoal("Запись во внешнюю организацию", "documents", AppointmentGoal.ScenarioType.EXTERNAL,
                AppointmentGoal.GoalGroup.OTHER_INSTITUTIONS, 80, false, "Запись оформляется через другую организацию.", List.of());
    }

    private void createGoal(String name, String icon, AppointmentGoal.ScenarioType scenario,
                            AppointmentGoal.GoalGroup group, int order, boolean paid,
                            String description, List<Specialty> eligibleSpecialties) {
        AppointmentGoal goal = new AppointmentGoal();
        goal.setName(name);
        goal.setIconKey(icon);
        goal.setScenarioType(scenario);
        goal.setGoalGroup(group);
        goal.setSortOrder(order);
        goal.setPaid(paid);
        goal.setActive(true);
        goal.setDescription(description);
        goal.setSpecialties(new ArrayList<>(eligibleSpecialties));
        goals.save(goal);
    }

    private User createUser(String username, String email, String surname, String firstName,
                            String middleName, RoleEnum role, Department department,
                            Specialty specialty, List<ClinicBranch> doctorBranches,
                            boolean onDutyOrvi) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("password"));
        user.setActive(true);
        user.setSurname(surname);
        user.setFirstName(firstName);
        user.setMiddleName(middleName);
        user.setEmail(email);
        user.setPhone("+7999" + String.format("%07d", Math.abs(username.hashCode()) % 10_000_000));
        user.setBirthDate(LocalDate.of(1980 + Math.abs(username.hashCode()) % 20, 1, 1));
        user.setAddress("г. Москва");
        user.setPolicyNumber("POL-" + username.toUpperCase().replace('_', '-'));
        user.setRoles(EnumSet.of(role));
        user.setIdRole(role == RoleEnum.ADMIN ? 1 : role == RoleEnum.DOCTOR ? 2 : 3);
        user.setDepartment(department);
        user.setSpecialty(specialty);
        user.setOnDutyOrvi(onDutyOrvi);
        if (role == RoleEnum.DOCTOR) {
            user.setDoctorDepartments(department == null ? new ArrayList<>() : new ArrayList<>(List.of(department)));
            user.setBranches(doctorBranches == null ? new ArrayList<>() : new ArrayList<>(doctorBranches));
        }
        return users.save(user);
    }

    private void seedSchedules(List<User> doctors) {
        for (User doctor : doctors) {
            for (int day = 1; day <= 5; day++) {
                DoctorWorkingHours shift = new DoctorWorkingHours();
                shift.setDoctor(doctor);
                shift.setDayOfWeek(day);
                shift.setStartTime(LocalTime.of(9, 0));
                shift.setEndTime(LocalTime.of(17, 0));
                shift = workingHours.save(shift);
                DoctorScheduleBreak lunch = new DoctorScheduleBreak();
                lunch.setWorkingHours(shift);
                lunch.setStartTime(LocalTime.of(13, 0));
                lunch.setEndTime(LocalTime.of(14, 0));
                scheduleBreaks.save(lunch);
            }
        }
    }

    private void seedDoctorAlexeyTestVisits(User doctor, List<User> patients,
                                            List<Department> departments, List<ClinicBranch> branches) {
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        for (int i = 0; i < 5 && i < patients.size(); i++) {
            User patient = patients.get(i);
            Appointment visit = newAppointment(patient, doctor, departments, branches,
                    today.minusDays(2 + i), LocalTime.of(10 + i, 0), "confirmed");
            visit.setComplaint("Тестовый прошедший приём без медицинского итога");
            appointments.save(visit);
        }
    }

    private void seedAppointmentsAndRecords(List<User> doctors, List<User> patients,
                                            List<Department> departments,
                                            List<ClinicBranch> branches,
                                            List<MedicalServiceEntity> services) {
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        int completedCount = Math.min(18, patients.size());
        for (int i = 0; i < completedCount; i++) {
            User patient = patients.get(i);
            User doctor = doctors.get(i % doctors.size());
            Appointment visit = newAppointment(patient, doctor, departments, branches,
                    today.minusDays(10 + i), LocalTime.of(9 + (i % 6), 0), "completed");
            visit.setComplaint("Плановый визит");
            visit = appointments.save(visit);
            MedicalRecord record = new MedicalRecord();
            record.setDiagnosis(diagnosisFor(doctor.getSpecialty()));
            record.setTreatment("Лечение назначено по результатам осмотра.");
            record.setRecommendations("Соблюдать режим и прийти на контрольный осмотр при необходимости.");
            record.setCreatedDate(visit.getAppointmentDate().plusMinutes(30));
            record.setAppointment(visit);
            record = medicalRecords.save(record);
            if (i % 2 == 0) {
                Prescription prescription = new Prescription();
                prescription.setMedicationName(i % 4 == 0 ? "Парацетамол" : "Магний B6");
                prescription.setDosage(i % 4 == 0 ? "500 мг" : "1 таблетка");
                prescription.setFrequency("2 раза в день");
                prescription.setDurationDays(7);
                prescription.setMedicalRecord(record);
                prescription.setService(services.get(0));
                prescriptions.save(prescription);
            }
        }

        LocalDateTime now = LocalDateTime.now(CLINIC_ZONE);
        for (int i = 0; i < 24; i++) {
            User patient = patients.get((i + 1) % patients.size());
            User doctor = doctors.get(i % doctors.size());
            LocalDate date = today.plusDays(1 + (i % 7));
            LocalTime time = LocalTime.of(9 + (i % 7), i % 2 == 0 ? 0 : 30);
            Appointment upcoming = newAppointment(patient, doctor, departments, branches,
                    date, time, i < 3 ? "confirmed" : "pending");
            upcoming.setComplaint(i % 3 == 0 ? "Консультация специалиста" : "Плановый приём");
            appointments.save(upcoming);
        }

        if (now.toLocalTime().isBefore(LocalTime.of(15, 30))) {
            for (int i = 0; i < Math.min(4, doctors.size()); i++) {
                User doctor = doctors.get(i);
                User patient = patients.get((i + 7) % patients.size());
                Appointment todayVisit = newAppointment(patient, doctor, departments, branches,
                        today, LocalTime.of(16, i * 15), "confirmed");
                todayVisit.setComplaint("Приём по записи");
                appointments.save(todayVisit);
            }
        }
    }

    private Appointment newAppointment(User patient, User doctor, List<Department> departments,
                                       List<ClinicBranch> branches, LocalDate date, LocalTime time,
                                       String status) {
        Appointment appointment = new Appointment();
        appointment.setUser(patient);
        appointment.setDoctor(doctor);
        appointment.setDepartment(doctor.getDepartment() == null ? departments.get(0) : doctor.getDepartment());
        appointment.setBranch(doctor.getBranches() == null || doctor.getBranches().isEmpty()
                ? branches.get(0) : doctor.getBranches().get(0));
        appointment.setAppointmentDate(date.atTime(time));
        appointment.setStatus(status);
        appointment.setComplaint("Плановый приём");
        appointment.setType(AppointmentType.DOCTOR_VISIT);
        appointment.setDurationMinutes(30);
        appointment.setRequiresReschedule(false);
        appointment.setCreatedByAdmin(true);
        return appointment;
    }

    private void seedAffiliations(List<User> patients, List<ClinicBranch> branches) {
        for (int i = 0; i < Math.min(12, patients.size()); i++) {
            PatientAffiliation affiliation = new PatientAffiliation();
            affiliation.setPatient(patients.get(i));
            affiliation.setMedicalCenterName(branches.get(i % branches.size()).getName());
            affiliation.setDoctorName("Прикрепление по филиалу");
            affiliation.setAttachedAt(LocalDate.now(CLINIC_ZONE).minusMonths(i + 1));
            affiliation.setStatus(PatientAffiliation.AffiliationStatus.ACTIVE);
            affiliations.save(affiliation);
        }
    }

    private String diagnosisFor(Specialty specialty) {
        return specialty == null ? "Профилактический осмотр" : "Осмотр: " + specialty.getSpecialtyName();
    }

    private String doctorUsername(Specialty specialty) {
        return switch (specialty.getSpecialtyName()) {
            case "Терапия" -> "doctor_therapy";
            case "Кардиология" -> "doctor_cardiology";
            case "Неврология" -> "doctor_neurology";
            case "Хирургия" -> "doctor_surgery";
            case "Офтальмология" -> "doctor_ophthalmology";
            case "Оториноларингология" -> "doctor_ent";
            case "Урология" -> "doctor_urology";
            case "Дерматология" -> "doctor_dermatology";
            case "Гинекология" -> "doctor_gynecology";
            case "Педиатрия" -> "doctor_pediatrics";
            case "Травматология" -> "doctor_traumatology";
            case "Эндокринология" -> "doctor_endocrinology";
            default -> "doctor_specialty_" + specialty.getIdSpecialty();
        };
    }

    private String doctorSurname(int index) {
        String[] values = {"Миронов", "Лебедев", "Волков", "Соколов", "Кузнецов", "Орлов",
                "Громов", "Фролов", "Белов", "Крылов", "Данилов"};
        return values[(index - 1) % values.length];
    }

    private String doctorFirstName(int index) {
        String[] values = {"Ирина", "Мария", "Дмитрий", "Ольга", "Николай", "Светлана",
                "Андрей", "Екатерина", "Виктор", "Наталья", "Павел"};
        return values[(index - 1) % values.length];
    }

    private String doctorMiddleName(int index) {
        String[] values = {"Павловна", "Андреевна", "Олегович", "Игоревна", "Сергеевич", "Романовна",
                "Владимирович", "Дмитриевна", "Алексеевич", "Викторовна", "Ильич"};
        return values[(index - 1) % values.length];
    }
}
