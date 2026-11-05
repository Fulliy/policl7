package com.example.medicalsystem.service;

import com.example.medicalsystem.model.Appointment;
import com.example.medicalsystem.model.AppointmentType;
import com.example.medicalsystem.model.MedicalRecord;
import com.example.medicalsystem.model.PatientAffiliation;
import com.example.medicalsystem.model.PatientReferral;
import com.example.medicalsystem.model.RoleEnum;
import com.example.medicalsystem.model.User;
import com.example.medicalsystem.repository.AppointmentRepository;
import com.example.medicalsystem.repository.MedicalRecordRepository;
import com.example.medicalsystem.repository.PatientAffiliationRepository;
import com.example.medicalsystem.repository.PatientReferralRepository;
import com.example.medicalsystem.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
@Profile("dev")
@Transactional
public class PatientPortalDemoDataSeeder implements CommandLineRunner {
    private static final String DEMO_VISIT_MARKER = "Демонстрационный приём для медкарты";

    private final UserRepository users;
    private final AppointmentRepository appointments;
    private final MedicalRecordRepository medicalRecords;
    private final PatientReferralRepository referrals;
    private final PatientAffiliationRepository affiliations;

    @Value("${app.demo-data.enabled:false}")
    private boolean demoDataEnabled;

    public PatientPortalDemoDataSeeder(UserRepository users,
                                       AppointmentRepository appointments,
                                       MedicalRecordRepository medicalRecords,
                                       PatientReferralRepository referrals,
                                       PatientAffiliationRepository affiliations) {
        this.users = users;
        this.appointments = appointments;
        this.medicalRecords = medicalRecords;
        this.referrals = referrals;
        this.affiliations = affiliations;
    }

    @Override
    public void run(String... args) {
        if (!demoDataEnabled) return;

        List<User> patients = users.findAll().stream()
                .filter(user -> user.getRoles() != null && user.getRoles().contains(RoleEnum.PATIENT))
                .sorted(Comparator.comparing(User::getUsername, Comparator.nullsLast(String::compareTo)))
                .toList();
        User emptyPatient = patients.stream()
                .filter(patient -> "user10".equals(patient.getUsername()))
                .filter(this::hasNoPortalData)
                .findFirst()
                .orElseGet(() -> patients.stream().filter(this::hasNoPortalData).findFirst().orElse(null));

        if (emptyPatient == null) {
            return;
        }

        Map<String, SeedPlan> plans = demoPlans();
        for (User patient : patients) {
            SeedPlan plan = plans.get(patient.getUsername());
            if (plan != null && !patient.getIdUser().equals(emptyPatient.getIdUser())) {
                seedMedicalHistory(patient);
                plan.referrals().forEach(value -> seedReferral(patient, value));
                plan.affiliations().forEach(value -> seedAffiliation(patient, value));
            }
        }

    }

    private boolean hasNoPortalData(User patient) {
        Integer patientId = patient.getIdUser();
        return medicalRecords.findAllForPatient(patientId).isEmpty()
                && referrals.findByPatient_IdUserOrderByReferralDateDesc(patientId).isEmpty()
                && affiliations.findByPatient_IdUserOrderByAttachedAtDesc(patientId).isEmpty();
    }

    private void seedMedicalHistory(User patient) {
        if (!medicalRecords.findAllForPatient(patient.getIdUser()).isEmpty()) return;

        Appointment demoVisit = appointments.findByUser_IdUser(patient.getIdUser()).stream()
                .filter(appointment -> DEMO_VISIT_MARKER.equals(appointment.getComplaint()))
                .findFirst().orElseGet(() -> {
                    User doctor = users.findByUsername("doctor").orElseGet(() -> users.findAll().stream()
                            .filter(user -> user.getRoles() != null && user.getRoles().contains(RoleEnum.DOCTOR))
                            .findFirst().orElse(null));
                    if (doctor == null) return null;
                    Appointment appointment = new Appointment();
                    appointment.setUser(patient);
                    appointment.setDoctor(doctor);
                    appointment.setDepartment(doctor.getDepartment());
                    appointment.setType(AppointmentType.DOCTOR_VISIT);
                    appointment.setAppointmentDate(LocalDate.of(2025, 4, 15).atTime(10, 0));
                    appointment.setStatus("completed");
                    appointment.setComplaint(DEMO_VISIT_MARKER);
                    appointment.setDurationMinutes(30);
                    return appointments.save(appointment);
                });

        if (demoVisit == null || !medicalRecords.findByAppointment_IdAppointment(demoVisit.getIdAppointment()).isEmpty()) {
            return;
        }
        MedicalRecord record = new MedicalRecord();
        record.setDiagnosis("Профилактический осмотр");
        record.setTreatment("Рекомендации по поддержанию общего самочувствия");
        record.setRecommendations("Плановый контрольный осмотр по необходимости");
        record.setCreatedDate(LocalDateTime.of(2025, 4, 15, 10, 30));
        record.setAppointment(demoVisit);
        medicalRecords.save(record);
    }

    private void seedReferral(User patient, ReferralSeed seed) {
        if (referrals.existsByPatient_IdUserAndReferralDateAndTargetAndIssuedByName(
                patient.getIdUser(), seed.date(), seed.target(), seed.doctor())) return;
        PatientReferral referral = new PatientReferral();
        referral.setPatient(patient);
        referral.setReferralDate(seed.date());
        referral.setTarget(seed.target());
        referral.setIssuedByName(seed.doctor());
        referral.setStatus(seed.status());
        referrals.save(referral);
    }

    private void seedAffiliation(User patient, AffiliationSeed seed) {
        if (affiliations.existsByPatient_IdUserAndMedicalCenterNameAndDoctorNameAndAttachedAt(
                patient.getIdUser(), seed.center(), seed.doctor(), seed.date())) return;
        PatientAffiliation affiliation = new PatientAffiliation();
        affiliation.setPatient(patient);
        affiliation.setMedicalCenterName(seed.center());
        affiliation.setDoctorName(seed.doctor());
        affiliation.setAttachedAt(seed.date());
        affiliation.setStatus(seed.status());
        affiliations.save(affiliation);
    }

    private Map<String, SeedPlan> demoPlans() {
        return Map.of(
                "patient", new SeedPlan(
                        List.of(
                                new ReferralSeed(LocalDate.of(2026, 1, 15), "Консультация врача-кардиолога", "Елена Викторовна Смирнова", PatientReferral.ReferralStatus.ACTIVE),
                                new ReferralSeed(LocalDate.of(2026, 2, 3), "Электрокардиография", "Елена Викторовна Смирнова", PatientReferral.ReferralStatus.COMPLETED)),
                        List.of(new AffiliationSeed("Медцентр «Забота», терапевтическое отделение", "Алексей Сергеевич Миронов", LocalDate.of(2025, 3, 12), PatientAffiliation.AffiliationStatus.ACTIVE))),
                "user4", new SeedPlan(
                        List.of(
                                new ReferralSeed(LocalDate.of(2026, 1, 20), "Консультация врача-невролога", "Ирина Павловна Лебедева", PatientReferral.ReferralStatus.ACTIVE),
                                new ReferralSeed(LocalDate.of(2026, 2, 11), "Магнитно-резонансная томография", "Ирина Павловна Лебедева", PatientReferral.ReferralStatus.ACTIVE),
                                new ReferralSeed(LocalDate.of(2026, 3, 2), "Повторная консультация невролога", "Ирина Павловна Лебедева", PatientReferral.ReferralStatus.COMPLETED)),
                        List.of(
                                new AffiliationSeed("Медцентр «Забота», филиал на Лесной", "Ирина Павловна Лебедева", LocalDate.of(2025, 5, 8), PatientAffiliation.AffiliationStatus.ACTIVE),
                                new AffiliationSeed("Центр семейной медицины «Ромашка»", "Мария Андреевна Волкова", LocalDate.of(2025, 8, 21), PatientAffiliation.AffiliationStatus.ENDED))),
                "user5", new SeedPlan(
                        List.of(
                                new ReferralSeed(LocalDate.of(2026, 1, 28), "Ультразвуковое исследование", "Дмитрий Олегович Соколов", PatientReferral.ReferralStatus.ACTIVE),
                                new ReferralSeed(LocalDate.of(2026, 2, 19), "Консультация врача-терапевта", "Дмитрий Олегович Соколов", PatientReferral.ReferralStatus.ACTIVE)),
                        List.of(new AffiliationSeed("Медцентр «Забота», терапевтическое отделение", "Дмитрий Олегович Соколов", LocalDate.of(2025, 6, 17), PatientAffiliation.AffiliationStatus.ACTIVE))));
    }

    private record SeedPlan(List<ReferralSeed> referrals, List<AffiliationSeed> affiliations) { }
    private record ReferralSeed(LocalDate date, String target, String doctor,
                                PatientReferral.ReferralStatus status) { }
    private record AffiliationSeed(String center, String doctor, LocalDate date,
                                   PatientAffiliation.AffiliationStatus status) { }
}
