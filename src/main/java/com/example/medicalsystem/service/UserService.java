package com.example.medicalsystem.service;

import com.example.medicalsystem.dto.RegistrationForm;
import com.example.medicalsystem.model.RoleEnum;
import com.example.medicalsystem.model.User;
import com.example.medicalsystem.repository.DepartmentRepository;
import com.example.medicalsystem.repository.SpecialtyRepository;
import com.example.medicalsystem.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.data.domain.Page;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class UserService {

    private final UserRepository repository;
    private final DepartmentRepository departmentRepository;
    private final SpecialtyRepository specialtyRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.demo-data.enabled:false}")
    private boolean demoDataEnabled;

    public UserService(UserRepository repository,
                       DepartmentRepository departmentRepository,
                       SpecialtyRepository specialtyRepository,
                       PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.departmentRepository = departmentRepository;
        this.specialtyRepository = specialtyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void init() {
        if (!demoDataEnabled) {
            return;
        }
        ensureDemoUser(
                "admin",
                "admin@clinic.local",
                "Иванов",
                "Администратор",
                "Системы",
                "+79001112200",
                EnumSet.of(RoleEnum.ADMIN)
        );
        ensureDemoUser(
                "doctor",
                "doctor@clinic.local",
                "Петров",
                "Врач",
                "Сергеевич",
                "+79001112201",
                EnumSet.of(RoleEnum.DOCTOR)
        );
        ensureDemoUser(
                "patient",
                "patient@clinic.local",
                "Сидорова",
                "Пациент",
                "Алексеевна",
                "+79001112202",
                EnumSet.of(RoleEnum.PATIENT)
        );

        long count = repository.count();
        for (int i = (int) count + 1; i <= 10; i++) {
            User user = new User();
            user.setUsername(generateUniqueUsername("user" + i, null));
            user.setPassword(passwordEncoder.encode("pass" + i + "123"));
            user.setActive(true);
            user.setSurname("Фамилия" + i);
            user.setFirstName("Имя" + i);
            user.setMiddleName("Отчество" + i);
            user.setBirthDate(LocalDate.of(1980 + i, 1, 10));
            user.setPhone("+790011122" + String.format("%02d", i));
            user.setAddress("г. Москва");
            user.setPolicyNumber("POLICY" + i);
            user.setEmail("user" + i + "@mail.ru");
            user.setRoles(EnumSet.of(RoleEnum.PATIENT));
            user.setIdRole(3);
            user.setDepartment(departmentRepository.findById(1).orElse(null));
            user.setSpecialty(specialtyRepository.findById(1).orElse(null));
            repository.save(user);
        }
    }

    public List<User> findAll() {
        return repository.findAll();
    }

    public Page<User> findPage(int page) {
        return repository.findAll(PaginationHelper.pageable(page));
    }

    public User findById(Integer id) {
        return repository.findById(id).orElse(null);
    }

    public User findByUsername(String username) {
        return repository.findByUsername(username).orElse(null);
    }

    public User save(User user) {
        prepareUserForSave(user);
        return repository.save(user);
    }

    public User register(RegistrationForm form) {
        if (repository.existsByUsername(form.getUsername())) {
            throw new IllegalArgumentException("Пользователь с таким логином уже существует");
        }
        if (repository.existsByEmail(form.getEmail())) {
            throw new IllegalArgumentException("Пользователь с таким email уже зарегистрирован");
        }

        User user = new User();
        user.setUsername(form.getUsername());
        user.setSurname(form.getSurname());
        user.setFirstName(form.getFirstName());
        user.setMiddleName(form.getMiddleName());
        user.setEmail(form.getEmail());
        user.setPhone(form.getPhone());
        user.setPassword(form.getPassword());
        user.setActive(true);
        user.setRoles(EnumSet.of(RoleEnum.PATIENT));
        user.setIdRole(3);
        prepareUserForSave(user);
        return repository.save(user);
    }

    public void updateRoles(Integer id, Set<RoleEnum> roles) {
        User user = repository.findById(id).orElseThrow();
        user.setRoles(normalizeRoles(roles));
        user.setIdRole(mapLegacyRoleId(user.getRoles()));
        repository.save(user);
    }

    public void updateUserFromAdmin(User user, Set<RoleEnum> roles) {
        User existingUser = repository.findById(user.getIdUser()).orElseThrow();
        existingUser.setUsername(generateUniqueUsername(user.getUsername(), existingUser.getIdUser()));
        existingUser.setRoles(normalizeRoles(roles));
        existingUser.setIdRole(mapLegacyRoleId(existingUser.getRoles()));
        repository.save(existingUser);
    }

    public void deleteById(Integer id) {
        repository.deleteById(id);
    }

    public Page<User> search(String fieldName, String value, int page) {
        if (fieldName == null || value == null || value.isBlank()) {
            return findPage(page);
        }

        String trimmedValue = value.trim();
        if ("id".equals(fieldName) || "idUser".equals(fieldName)) {
            return parseId(trimmedValue)
                    .map(id -> repository.findById(id).map(List::of).orElseGet(List::of))
                    .map(items -> PaginationHelper.pageFromList(items, page))
                    .orElseGet(() -> PaginationHelper.pageFromList(List.of(), page));
        }

        if ("surname".equals(fieldName)) {
            return PaginationHelper.pageFromList(repository.findBySurnameContainingIgnoreCase(trimmedValue), page);
        }

        if ("firstName".equals(fieldName)) {
            return PaginationHelper.pageFromList(repository.findByFirstNameContainingIgnoreCase(trimmedValue), page);
        }

        if ("username".equals(fieldName)) {
            return PaginationHelper.pageFromList(
                    repository.findByUsername(trimmedValue).map(List::of).orElseGet(List::of),
                    page
            );
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

    private void prepareUserForSave(User user) {
        User existingUser = user.getIdUser() == null ? null : findById(user.getIdUser());

        if (existingUser != null && (user.getRoles() == null || user.getRoles().isEmpty())) {
            user.setRoles(existingUser.getRoles());
        }

        user.setRoles(normalizeRoles(user.getRoles()));
        user.setIdRole(mapLegacyRoleId(user.getRoles()));
        user.setUsername(generateUniqueUsername(user.getUsername(), user.getIdUser()));

        if (existingUser != null && (user.getPassword() == null || user.getPassword().isBlank())) {
            user.setPassword(existingUser.getPassword());
        } else if (!isEncoded(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
    }

    private void ensureDemoUser(String username,
                                String email,
                                String surname,
                                String firstName,
                                String middleName,
                                String phone,
                                Set<RoleEnum> roles) {
        if (repository.findByUsername(username).isPresent() || repository.findByEmail(email).isPresent()) {
            return;
        }
        User user = new User();

        user.setUsername(username);
        user.setSurname(surname);
        user.setFirstName(firstName);
        user.setMiddleName(middleName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode("password"));
        user.setActive(true);
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user.setAddress("г. Москва");
        user.setPolicyNumber("AUTO-" + username);
        user.setRoles(roles);
        user.setIdRole(mapLegacyRoleId(roles));
        user.setDepartment(departmentRepository.findById(1).orElse(null));
        user.setSpecialty(specialtyRepository.findById(1).orElse(null));
        repository.save(user);
    }

    private Set<RoleEnum> normalizeRoles(Set<RoleEnum> roles) {
        return roles == null || roles.isEmpty() ? EnumSet.of(RoleEnum.PATIENT) : EnumSet.copyOf(roles);
    }

    private Integer mapLegacyRoleId(Set<RoleEnum> roles) {
        if (roles.contains(RoleEnum.ADMIN)) {
            return 1;
        }
        if (roles.contains(RoleEnum.DOCTOR)) {
            return 2;
        }
        return 3;
    }

    private String generateUniqueUsername(String username, Integer currentUserId) {
        return generateUniqueUsername(username, currentUserId, null);
    }

    private String generateUniqueUsername(String username, Integer currentUserId, Set<String> reservedUsernames) {
        String sanitized = (username == null || username.isBlank()) ? "user" : username.trim();
        String candidate = sanitized;
        int suffix = 1;
        while (isUsernameTaken(candidate, currentUserId, reservedUsernames)) {
            candidate = sanitized + suffix++;
        }
        if (reservedUsernames != null) {
            reservedUsernames.add(candidate);
        }
        return candidate;
    }

    private boolean isUsernameTaken(String candidate, Integer currentUserId, Set<String> reservedUsernames) {
        if (reservedUsernames != null && reservedUsernames.contains(candidate)) {
            return true;
        }
        return repository.findByUsername(candidate)
                .filter(user -> !user.getIdUser().equals(currentUserId))
                .isPresent();
    }

    private boolean isEncoded(String password) {
        return password != null
                && (password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$"));
    }
}
