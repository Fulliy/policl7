package com.example.medicalsystem.service;

import com.example.medicalsystem.model.MedicalRecord;
import com.example.medicalsystem.repository.AppointmentRepository;
import com.example.medicalsystem.repository.MedicalRecordRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class MedicalRecordService {

    private final MedicalRecordRepository repository;
    private final AppointmentRepository appointmentRepository;

    @Value("${app.demo-data.enabled:false}")
    private boolean demoDataEnabled;

    @Value("${app.demo-data.reset:false}")
    private boolean demoDataReset;

    public MedicalRecordService(MedicalRecordRepository repository,
                                AppointmentRepository appointmentRepository) {
        this.repository = repository;
        this.appointmentRepository = appointmentRepository;
    }

    @PostConstruct
    public void init() {
        if (!demoDataEnabled || demoDataReset) {
            return;
        }
        long count = repository.count();
        for (int i = (int) count + 1; i <= 10; i++) {
            MedicalRecord record = new MedicalRecord();
            record.setDiagnosis("Диагноз " + i);
            record.setTreatment("Лечение " + i);
            record.setRecommendations("Рекомендации " + i);
            record.setCreatedDate(LocalDateTime.now().minusDays(i));
            record.setAppointment(appointmentRepository.findById(1).orElse(null));
            repository.save(record);
        }
    }

    public List<MedicalRecord> findAll() {
        return repository.findAll();
    }

    public Page<MedicalRecord> findPage(int page) {
        return repository.findAll(PaginationHelper.pageable(page));
    }

    public MedicalRecord findById(Integer id) {
        return repository.findById(id).orElse(null);
    }

    public MedicalRecord save(MedicalRecord record) {
        return repository.save(record);
    }

    public void deleteById(Integer id) {
        repository.deleteById(id);
    }

    public Page<MedicalRecord> search(String fieldName, String value, int page) {
        if (fieldName == null || value == null || value.isBlank()) {
            return findPage(page);
        }

        String trimmedValue = value.trim();
        if ("id".equals(fieldName) || "idRecord".equals(fieldName)) {
            return parseId(trimmedValue)
                    .map(id -> repository.findById(id).map(List::of).orElseGet(List::of))
                    .map(items -> PaginationHelper.pageFromList(items, page))
                    .orElseGet(() -> PaginationHelper.pageFromList(List.of(), page));
        }

        if ("name".equals(fieldName) || "diagnosis".equals(fieldName)) {
            return PaginationHelper.pageFromList(repository.findByDiagnosisContainingIgnoreCase(trimmedValue), page);
        }

        return PaginationHelper.pageFromList(repository.searchByKeyword(trimmedValue), page);
    }

    public List<MedicalRecord> findByAppointment(Integer idAppointment) {
        return repository.findByAppointment_IdAppointment(idAppointment);
    }

    public List<MedicalRecord> findByAppointments(List<Integer> appointmentIds) {
        if (appointmentIds == null || appointmentIds.isEmpty()) return List.of();
        return repository.findByAppointment_IdAppointmentIn(appointmentIds);
    }

    public List<MedicalRecord> findAllForPatient(Integer patientId) {
        return repository.findAllForPatient(patientId);
    }

    private Optional<Integer> parseId(String value) {
        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
