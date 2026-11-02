package com.example.medicalsystem.identity.api;

import com.example.medicalsystem.identity.model.RoleEnum;
import com.example.medicalsystem.identity.model.UserAccount;
import com.example.medicalsystem.identity.repository.UserAccountRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@RestController
@Tag(name = "Учётные записи")
public class IdentityApiController {

    private static final Logger log = LoggerFactory.getLogger(IdentityApiController.class);
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;

    public IdentityApiController(UserAccountRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/api/users/me")
    @Operation(summary = "Профиль текущего пользователя")
    public UserDto me(@AuthenticationPrincipal UserDetails principal) {
        return toDto(requireByUsername(principal.getUsername()));
    }

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Зарегистрировать пациента")
    public UserDto register(@Valid @RequestBody RegistrationRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пароли не совпадают");
        }
        if (users.existsByUsername(request.username()) || users.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Логин или email уже зарегистрирован");
        }
        UserAccount account = new UserAccount();
        account.setUsername(request.username());
        account.setPassword(passwordEncoder.encode(request.password()));
        account.setActive(true);
        account.setSurname(request.surname());
        account.setFirstName(request.firstName());
        account.setMiddleName(request.middleName());
        account.setEmail(request.email());
        account.setPhone(request.phone());
        account.setRoles(Set.of(RoleEnum.PATIENT));
        account.setIdRole(3);
        UserDto created = toDto(users.save(account));
        log.info("Registered patient account id={}", created.id());
        return created;
    }

    @GetMapping("/api/admin/users")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Список пользователей для администратора")
    public List<UserDto> listUsers() {
        return users.findAll().stream().map(IdentityApiController::toDto).toList();
    }

    @GetMapping("/api/admin/users/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Пользователь по ID для администратора")
    public UserDto getUser(@PathVariable Integer id) {
        return users.findById(id).map(IdentityApiController::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PutMapping("/api/admin/users/{id}/roles")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Изменить роли пользователя")
    public UserDto updateRoles(@PathVariable Integer id, @RequestBody RoleUpdateRequest request) {
        UserAccount account = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (request.roles() == null || request.roles().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нужна хотя бы одна роль");
        }
        account.setRoles(Set.copyOf(request.roles()));
        account.setIdRole(account.getRoles().contains(RoleEnum.ADMIN) ? 1
                : account.getRoles().contains(RoleEnum.DOCTOR) ? 2 : 3);
        return toDto(users.save(account));
    }

    private UserAccount requireByUsername(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static UserDto toDto(UserAccount user) {
        return new UserDto(user.getIdUser(), user.getUsername(), user.isActive(), user.getSurname(),
                user.getFirstName(), user.getMiddleName(), user.getBirthDate(), user.getPhone(),
                user.getAddress(), user.getPolicyNumber(), user.getEmail(), user.getDepartmentId(),
                user.getSpecialtyId(), user.getRoles());
    }

    public record UserDto(Integer id, String username, boolean active, String surname, String firstName,
                          String middleName, java.time.LocalDate birthDate, String phone, String address,
                          String policyNumber, String email, Integer departmentId, Integer specialtyId,
                          Set<RoleEnum> roles) { }

    public record RegistrationRequest(@NotBlank @Size(min = 3, max = 50) String username,
                                      @NotBlank @Size(min = 6, max = 100) String password,
                                      @NotBlank String confirmPassword,
                                      @NotBlank @Size(max = 50) String surname,
                                      @NotBlank @Size(max = 50) String firstName,
                                      @Size(max = 50) String middleName,
                                      @Email @NotBlank @Size(max = 100) String email,
                                      String phone) { }
    public record RoleUpdateRequest(Set<RoleEnum> roles) { }
}
