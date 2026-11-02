package com.example.medicalsystem.service;

import com.example.medicalsystem.model.Prescription;
import com.example.medicalsystem.repository.MedicalRecordRepository;
import com.example.medicalsystem.repository.PrescriptionRepository;
import com.example.medicalsystem.repository.ServiceRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class PrescriptionService {

    private final PrescriptionRepository repository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final ServiceRepository serviceRepository;

    public PrescriptionService(PrescriptionRepository repository,
                               MedicalRecordRepository medicalRecordRepository,
                               ServiceRepository serviceRepository) {
        this.repository = repository;
        this.medicalRecordRepository = medicalRecordRepository;
        this.serviceRepository = serviceRepository;
    }

    @PostConstruct
    public void init() {
        long count = repository.count();
        for (int i = (int) count + 1; i <= 10; i++) {
            Prescription prescription = new Prescription();
            prescription.setMedicationName("Лекарство " + i);
            prescription.setDosage(i + "00мг");
            prescription.setFrequency("1 раз в день");
            prescription.setDurationDays(i);
            prescription.setMedicalRecord(medicalRecordRepository.findById(1).orElse(null));
            prescription.setService(serviceRepository.findById(1).orElse(null));
            repository.save(prescription);
        }
    }

    public List<Prescription> findAll() {
        return repository.findAll();
    }

    public Page<Prescription> findPage(int page) {
        return repository.findAll(PaginationHelper.pageable(page));
    }

    public Prescription findById(Integer id) {
        return repository.findById(id).orElse(null);
    }

    public Prescription save(Prescription prescription) {
        return repository.save(prescription);
    }

    public void deleteById(Integer id) {
        repository.deleteById(id);
    }

    public Page<Prescription> search(String fieldName, String value, int page) {
        if (fieldName == null || value == null || value.isBlank()) {
            return findPage(page);
        }

        String trimmedValue = value.trim();
        if ("id".equals(fieldName) || "idPrescription".equals(fieldName)) {
            return parseId(trimmedValue)
                    .map(id -> repository.findById(id).map(List::of).orElseGet(List::of))
                    .map(items -> PaginationHelper.pageFromList(items, page))
                    .orElseGet(() -> PaginationHelper.pageFromList(List.of(), page));
        }

        if ("name".equals(fieldName) || "medicationName".equals(fieldName)) {
            return PaginationHelper.pageFromList(repository.findByMedicationNameContainingIgnoreCase(trimmedValue), page);
        }

        return PaginationHelper.pageFromList(repository.searchByKeyword(trimmedValue), page);
    }

    public List<Prescription> findByMedicalRecord(Integer idRecord) {
        return repository.findByMedicalRecord_IdRecord(idRecord);
    }

    public List<Prescription> findByService(Integer idService) {
        return repository.findByService_IdService(idService);
    }

    private Optional<Integer> parseId(String value) {
        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
