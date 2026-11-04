package com.example.medicalsystem.service;

import com.example.medicalsystem.model.Specialty;
import com.example.medicalsystem.repository.SpecialtyRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class SpecialtyService {

    private final SpecialtyRepository repository;

    @Value("${app.demo-data.enabled:false}")
    private boolean demoDataEnabled;

    public SpecialtyService(SpecialtyRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void init() {
        if (!demoDataEnabled) {
            return;
        }
        long count = repository.count();
        for (int i = (int) count + 1; i <= 10; i++) {
            Specialty specialty = new Specialty();
            specialty.setSpecialtyName("Специальность " + i);
            specialty.setDescription("Описание специальности " + i);
            repository.save(specialty);
        }
    }

    public List<Specialty> findAll() {
        return repository.findAll();
    }

    public Page<Specialty> findPage(int page) {
        return repository.findAll(PaginationHelper.pageable(page));
    }

    public Specialty findById(Integer id) {
        return repository.findById(id).orElse(null);
    }

    public Specialty save(Specialty specialty) {
        return repository.save(specialty);
    }

    public void deleteById(Integer id) {
        repository.deleteById(id);
    }

    public Page<Specialty> search(String fieldName, String value, int page) {
        if (fieldName == null || value == null || value.isBlank()) {
            return findPage(page);
        }

        String trimmedValue = value.trim();
        if ("id".equals(fieldName) || "idSpecialty".equals(fieldName)) {
            return parseId(trimmedValue)
                    .map(id -> repository.findById(id).map(List::of).orElseGet(List::of))
                    .map(items -> PaginationHelper.pageFromList(items, page))
                    .orElseGet(() -> PaginationHelper.pageFromList(List.of(), page));
        }

        if ("name".equals(fieldName) || "specialtyName".equals(fieldName)) {
            return PaginationHelper.pageFromList(repository.findBySpecialtyNameContainingIgnoreCase(trimmedValue), page);
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
