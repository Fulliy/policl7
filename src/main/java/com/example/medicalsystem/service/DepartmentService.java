package com.example.medicalsystem.service;

import com.example.medicalsystem.model.Department;
import com.example.medicalsystem.repository.DepartmentRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class DepartmentService {

    private final DepartmentRepository repository;

    @Value("${app.demo-data.enabled:false}")
    private boolean demoDataEnabled;

    @Value("${app.demo-data.reset:false}")
    private boolean demoDataReset;

    public DepartmentService(DepartmentRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void init() {
        if (!demoDataEnabled || demoDataReset) {
            return;
        }
        long count = repository.count();
        for (int i = (int) count + 1; i <= 10; i++) {
            Department department = new Department();
            department.setDepartmentName("Отделение " + i);
            department.setFloor(i % 5 + 1);
            department.setRoomNumber(String.valueOf(100 + i));
            repository.save(department);
        }
    }

    public List<Department> findAll() {
        return repository.findAll();
    }

    public Page<Department> findPage(int page) {
        return repository.findAll(PaginationHelper.pageable(page));
    }

    public Department findById(Integer id) {
        return repository.findById(id).orElse(null);
    }

    public Department save(Department department) {
        return repository.save(department);
    }

    public void deleteById(Integer id) {
        repository.deleteById(id);
    }

    public Page<Department> search(String fieldName, String value, int page) {
        if (fieldName == null || value == null || value.isBlank()) {
            return findPage(page);
        }

        String trimmedValue = value.trim();
        if ("id".equals(fieldName) || "idDepartment".equals(fieldName)) {
            return parseId(trimmedValue)
                    .map(id -> repository.findById(id).map(List::of).orElseGet(List::of))
                    .map(items -> PaginationHelper.pageFromList(items, page))
                    .orElseGet(() -> PaginationHelper.pageFromList(List.of(), page));
        }

        if ("name".equals(fieldName) || "departmentName".equals(fieldName)) {
            return PaginationHelper.pageFromList(repository.findByDepartmentNameContainingIgnoreCase(trimmedValue), page);
        }

        return PaginationHelper.pageFromList(repository.searchByKeyword(trimmedValue), page);
    }

    public List<Department> findByFloor(Integer floor) {
        return repository.findByFloor(floor);
    }

    private Optional<Integer> parseId(String value) {
        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
