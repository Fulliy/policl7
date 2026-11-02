package com.example.medicalsystem.service;

import com.example.medicalsystem.model.Role;
import com.example.medicalsystem.repository.RoleRepository;
import com.example.medicalsystem.repository.SpecialtyRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class RoleService {

    private final RoleRepository repository;
    private final SpecialtyRepository specialtyRepository;

    public RoleService(RoleRepository repository,
                       SpecialtyRepository specialtyRepository) {
        this.repository = repository;
        this.specialtyRepository = specialtyRepository;
    }

    @PostConstruct
    public void init() {
        long count = repository.count();
        for (int i = (int) count + 1; i <= 10; i++) {
            Role role = new Role();
            role.setRoleName("Роль " + i);
            role.setDescription("Описание роли " + i);
            role.setSpecialty(i == 1 ? specialtyRepository.findById(1).orElse(null) : null);
            repository.save(role);
        }
    }

    public List<Role> findAll() {
        return repository.findAll();
    }

    public Page<Role> findPage(int page) {
        return repository.findAll(PaginationHelper.pageable(page));
    }

    public Role findById(Integer id) {
        return repository.findById(id).orElse(null);
    }

    public Role save(Role role) {
        return repository.save(role);
    }

    public void deleteById(Integer id) {
        repository.deleteById(id);
    }

    public Page<Role> search(String fieldName, String value, int page) {
        if (fieldName == null || value == null || value.isBlank()) {
            return findPage(page);
        }

        String trimmedValue = value.trim();
        if ("id".equals(fieldName) || "idRole".equals(fieldName)) {
            return parseId(trimmedValue)
                    .map(id -> repository.findById(id).map(List::of).orElseGet(List::of))
                    .map(items -> PaginationHelper.pageFromList(items, page))
                    .orElseGet(() -> PaginationHelper.pageFromList(List.of(), page));
        }

        if ("name".equals(fieldName) || "roleName".equals(fieldName)) {
            return PaginationHelper.pageFromList(repository.findByRoleNameContainingIgnoreCase(trimmedValue), page);
        }

        return PaginationHelper.pageFromList(repository.searchByKeyword(trimmedValue), page);
    }

    public List<Role> findBySpecialty(Integer idSpecialty) {
        return repository.findBySpecialty_IdSpecialty(idSpecialty);
    }

    private Optional<Integer> parseId(String value) {
        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
