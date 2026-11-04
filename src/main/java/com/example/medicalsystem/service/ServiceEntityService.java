package com.example.medicalsystem.service;

import com.example.medicalsystem.model.MedicalServiceEntity;
import com.example.medicalsystem.repository.ServiceRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ServiceEntityService {

    private final ServiceRepository repository;

    @Value("${app.demo-data.enabled:false}")
    private boolean demoDataEnabled;

    public ServiceEntityService(ServiceRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void init() {
        if (!demoDataEnabled) {
            return;
        }
        String[] names = {"Консультация терапевта", "УЗИ", "МРТ", "Анализ крови", "ЭКГ",
                "Консультация кардиолога", "Консультация невролога", "Консультация травматолога",
                "Рентгенография", "Консультация педиатра", "Общий анализ мочи"};
        int[] costs = {1500, 2500, 5000, 800, 1200, 1600, 1700, 1800, 1900, 2000, 400};
        if (repository.count() == 0) {
            for (int i = 0; i < names.length; i++) {
                repository.save(new MedicalServiceEntity(null, names[i], costs[i], names[i]));
            }
            return;
        }
        for (int i = 0; i < names.length; i++) {
            int id = i + 1;
            repository.findById(id).ifPresent(service -> {
                service.setServiceName(names[id - 1]);
                repository.save(service);
            });
        }
    }

    public List<MedicalServiceEntity> findAll() {
        return repository.findAll();
    }

    public Page<MedicalServiceEntity> findPage(int page) {
        return repository.findAll(PaginationHelper.pageable(page));
    }

    public MedicalServiceEntity findById(Integer id) {
        return repository.findById(id).orElse(null);
    }

    public MedicalServiceEntity save(MedicalServiceEntity service) {
        return repository.save(service);
    }

    public void deleteById(Integer id) {
        repository.deleteById(id);
    }

    public Page<MedicalServiceEntity> search(String fieldName, String value, int page) {
        if (fieldName == null || value == null || value.isBlank()) {
            return findPage(page);
        }

        String trimmedValue = value.trim();
        if ("id".equals(fieldName) || "idService".equals(fieldName)) {
            return parseId(trimmedValue)
                    .map(id -> repository.findById(id).map(List::of).orElseGet(List::of))
                    .map(items -> PaginationHelper.pageFromList(items, page))
                    .orElseGet(() -> PaginationHelper.pageFromList(List.of(), page));
        }

        if ("name".equals(fieldName) || "serviceName".equals(fieldName)) {
            return PaginationHelper.pageFromList(repository.findByServiceNameContainingIgnoreCase(trimmedValue), page);
        }

        return PaginationHelper.pageFromList(repository.searchByKeyword(trimmedValue), page);
    }

    private Optional<Integer> parseId(String value) {
        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
