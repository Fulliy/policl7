package com.example.medicalsystem.api;

import com.example.medicalsystem.model.*;
import com.example.medicalsystem.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.stereotype.Service;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import jakarta.validation.Valid;

@Service
public class CatalogApiService {

    private final DepartmentService departmentService;
    private final MedicalRecordService medicalRecordService;
    private final ServiceEntityService serviceEntityService;
    private final PrescriptionService prescriptionService;
    private final RoleService roleService;
    private final SpecialtyService specialtyService;
    private final UserService userService;
    private final AppointmentService appointmentService;

    public CatalogApiService(DepartmentService departmentService,
                                MedicalRecordService medicalRecordService,
                                ServiceEntityService serviceEntityService,
                                PrescriptionService prescriptionService,
                                RoleService roleService,
                                SpecialtyService specialtyService,
                                UserService userService,
                                AppointmentService appointmentService) {
        this.departmentService = departmentService;
        this.medicalRecordService = medicalRecordService;
        this.serviceEntityService = serviceEntityService;
        this.prescriptionService = prescriptionService;
        this.roleService = roleService;
        this.specialtyService = specialtyService;
        this.userService = userService;
        this.appointmentService = appointmentService;
    }

    @GetMapping("/departments")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Tag(name = "Отделения")
    @Operation(summary = "Получить список отделений")
    public List<DepartmentDto> departments() {
        return departmentService.findAll().stream().map(CatalogApiService::toDto).toList();
    }

    @GetMapping("/departments/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Tag(name = "Отделения")
    @Operation(summary = "Получить отделение по ID")
    public DepartmentDto department(@PathVariable Integer id) {
        return toDto(require(departmentService.findById(id)));
    }

    @PostMapping("/departments")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Tag(name = "Отделения")
    @Operation(summary = "Создать отделение")
    public DepartmentDto createDepartment(@Valid @RequestBody DepartmentDto request) {
        Department value = new Department();
        value.setDepartmentName(request.departmentName());
        value.setFloor(request.floor());
        value.setRoomNumber(request.roomNumber());
        return toDto(departmentService.save(value));
    }

    @PutMapping("/departments/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Tag(name = "Отделения")
    @Operation(summary = "Изменить отделение")
    public DepartmentDto updateDepartment(@PathVariable Integer id, @Valid @RequestBody DepartmentDto request) {
        Department value = require(departmentService.findById(id));
        value.setDepartmentName(request.departmentName());
        value.setFloor(request.floor());
        value.setRoomNumber(request.roomNumber());
        return toDto(departmentService.save(value));
    }

    @DeleteMapping("/departments/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Tag(name = "Отделения")
    @Operation(summary = "Удалить отделение")
    public void deleteDepartment(@PathVariable Integer id) {
        require(departmentService.findById(id));
        departmentService.deleteById(id);
    }

    @GetMapping("/medical-records")
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'PATIENT')")
    @Tag(name = "Медицинские записи")
    @Operation(summary = "Получить список медицинских записей")
    public List<MedicalRecordDto> medicalRecords(org.springframework.security.core.Authentication auth) {
        if (hasAuthority(auth, "PATIENT") && !hasAuthority(auth, "DOCTOR") && !hasAuthority(auth, "ADMIN")) {
            User patient = userService.findByUsername(auth.getName());
            return appointmentService.findByUser(patient.getIdUser()).stream()
                    .flatMap(appointment -> medicalRecordService.findByAppointment(appointment.getIdAppointment()).stream())
                    .map(CatalogApiService::toDto).toList();
        }
        if (hasAuthority(auth, "DOCTOR") && !hasAuthority(auth, "ADMIN")) {
            User doctor = userService.findByUsername(auth.getName());
            return appointmentService.findByDoctor(doctor.getIdUser()).stream()
                    .flatMap(appointment -> medicalRecordService.findByAppointment(appointment.getIdAppointment()).stream())
                    .map(CatalogApiService::toDto).toList();
        }
        throw new org.springframework.security.access.AccessDeniedException("Доступ к медицинским данным запрещён");
    }

    @GetMapping("/medical-records/{id}")
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'PATIENT')")
    @Tag(name = "Медицинские записи")
    @Operation(summary = "Получить медицинскую запись по ID")
    public MedicalRecordDto medicalRecord(@PathVariable Integer id, org.springframework.security.core.Authentication auth) {
        MedicalRecord record = require(medicalRecordService.findById(id));
        ensureMedicalRecordAccess(record, auth);
        return toDto(record);
    }

    @PostMapping("/medical-records")
    @PreAuthorize("hasAuthority('DOCTOR')")
    @Tag(name = "Медицинские записи")
    @Operation(summary = "Создать медицинскую запись")
    public MedicalRecordDto createMedicalRecord(@Valid @RequestBody MedicalRecordDto request, org.springframework.security.core.Authentication auth) {
        requireAssignedDoctor(auth);
        Appointment appointment = require(appointmentService.findById(request.appointmentId()));
        ensureAppointmentAccess(appointment, auth);
        MedicalRecord value = new MedicalRecord();
        value.setDiagnosis(request.diagnosis());
        value.setTreatment(request.treatment());
        value.setRecommendations(request.recommendations());
        value.setCreatedDate(request.createdDate());
        value.setAppointment(appointment);
        return toDto(medicalRecordService.save(value));
    }

    @PutMapping("/medical-records/{id}")
    @PreAuthorize("hasAuthority('DOCTOR')")
    @Tag(name = "Медицинские записи")
    @Operation(summary = "Изменить медицинскую запись")
    public MedicalRecordDto updateMedicalRecord(@PathVariable Integer id, @Valid @RequestBody MedicalRecordDto request, org.springframework.security.core.Authentication auth) {
        requireAssignedDoctor(auth);
        MedicalRecord value = require(medicalRecordService.findById(id));
        ensureMedicalRecordAccess(value, auth);
        Appointment targetAppointment = require(appointmentService.findById(request.appointmentId()));
        ensureAppointmentAccess(targetAppointment, auth);
        value.setDiagnosis(request.diagnosis());
        value.setTreatment(request.treatment());
        value.setRecommendations(request.recommendations());
        value.setCreatedDate(request.createdDate());
        value.setAppointment(targetAppointment);
        return toDto(medicalRecordService.save(value));
    }

    @DeleteMapping("/medical-records/{id}")
    @PreAuthorize("hasAuthority('DOCTOR')")
    @Tag(name = "Медицинские записи")
    @Operation(summary = "Удалить медицинскую запись")
    public void deleteMedicalRecord(@PathVariable Integer id, org.springframework.security.core.Authentication auth) {
        requireAssignedDoctor(auth);
        ensureMedicalRecordAccess(require(medicalRecordService.findById(id)), auth);
        medicalRecordService.deleteById(id);
    }

    @GetMapping("/services")
    @PreAuthorize("isAuthenticated()")
    @Tag(name = "Медицинские услуги")
    @Operation(summary = "Получить список медицинских услуг")
    public List<ServiceDto> services() {
        return serviceEntityService.findAll().stream().map(CatalogApiService::toDto).toList();
    }

    @GetMapping("/services/{id}")
    @PreAuthorize("isAuthenticated()")
    @Tag(name = "Медицинские услуги")
    @Operation(summary = "Получить медицинскую услугу по ID")
    public ServiceDto service(@PathVariable Integer id) {
        return toDto(require(serviceEntityService.findById(id)));
    }

    @PostMapping("/services")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DOCTOR')")
    @Tag(name = "Медицинские услуги")
    @Operation(summary = "Создать медицинскую услугу")
    public ServiceDto createService(@Valid @RequestBody ServiceDto request) {
        MedicalServiceEntity value = new MedicalServiceEntity();
        value.setServiceName(request.serviceName());
        value.setCost(request.cost());
        value.setDescription(request.description());
        return toDto(serviceEntityService.save(value));
    }

    @PutMapping("/services/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DOCTOR')")
    @Tag(name = "Медицинские услуги")
    @Operation(summary = "Изменить медицинскую услугу")
    public ServiceDto updateService(@PathVariable Integer id, @Valid @RequestBody ServiceDto request) {
        MedicalServiceEntity value = require(serviceEntityService.findById(id));
        value.setServiceName(request.serviceName());
        value.setCost(request.cost());
        value.setDescription(request.description());
        return toDto(serviceEntityService.save(value));
    }

    @DeleteMapping("/services/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DOCTOR')")
    @Tag(name = "Медицинские услуги")
    @Operation(summary = "Удалить медицинскую услугу")
    public void deleteService(@PathVariable Integer id) {
        require(serviceEntityService.findById(id));
        serviceEntityService.deleteById(id);
    }

    @GetMapping("/prescriptions")
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'PATIENT')")
    @Tag(name = "Назначения")
    @Operation(summary = "Получить список назначений")
    public List<PrescriptionDto> prescriptions(org.springframework.security.core.Authentication auth) {
        return visiblePrescriptions(auth);
    }

    @GetMapping("/prescriptions/{id}")
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'PATIENT')")
    @Tag(name = "Назначения")
    @Operation(summary = "Получить назначение по ID")
    public PrescriptionDto prescription(@PathVariable Integer id, org.springframework.security.core.Authentication auth) {
        Prescription value = require(prescriptionService.findById(id));
        ensureMedicalRecordAccess(require(medicalRecordService.findById(value.getMedicalRecord().getIdRecord())), auth);
        return toDto(value);
    }

    @PostMapping("/prescriptions")
    @PreAuthorize("hasAuthority('DOCTOR')")
    @Tag(name = "Назначения")
    @Operation(summary = "Создать назначение")
    public PrescriptionDto createPrescription(@Valid @RequestBody PrescriptionDto request, org.springframework.security.core.Authentication auth) {
        requireAssignedDoctor(auth);
        ensureMedicalRecordAccess(require(medicalRecordService.findById(request.medicalRecordId())), auth);
        Prescription value = new Prescription();
        value.setMedicationName(request.medicationName());
        value.setDosage(request.dosage());
        value.setFrequency(request.frequency());
        value.setDurationDays(request.durationDays());
        value.setMedicalRecord(require(medicalRecordService.findById(request.medicalRecordId())));
        value.setService(require(serviceEntityService.findById(request.serviceId())));
        return toDto(prescriptionService.save(value));
    }

    @PutMapping("/prescriptions/{id}")
    @PreAuthorize("hasAuthority('DOCTOR')")
    @Tag(name = "Назначения")
    @Operation(summary = "Изменить назначение")
    public PrescriptionDto updatePrescription(@PathVariable Integer id, @Valid @RequestBody PrescriptionDto request, org.springframework.security.core.Authentication auth) {
        requireAssignedDoctor(auth);
        Prescription value = require(prescriptionService.findById(id));
        ensureMedicalRecordAccess(require(medicalRecordService.findById(value.getMedicalRecord().getIdRecord())), auth);
        ensureMedicalRecordAccess(require(medicalRecordService.findById(request.medicalRecordId())), auth);
        value.setMedicationName(request.medicationName());
        value.setDosage(request.dosage());
        value.setFrequency(request.frequency());
        value.setDurationDays(request.durationDays());
        value.setMedicalRecord(require(medicalRecordService.findById(request.medicalRecordId())));
        value.setService(require(serviceEntityService.findById(request.serviceId())));
        return toDto(prescriptionService.save(value));
    }

    @DeleteMapping("/prescriptions/{id}")
    @PreAuthorize("hasAuthority('DOCTOR')")
    @Tag(name = "Назначения")
    @Operation(summary = "Удалить назначение")
    public void deletePrescription(@PathVariable Integer id, org.springframework.security.core.Authentication auth) {
        requireAssignedDoctor(auth);
        Prescription value = require(prescriptionService.findById(id));
        ensureMedicalRecordAccess(require(medicalRecordService.findById(value.getMedicalRecord().getIdRecord())), auth);
        prescriptionService.deleteById(id);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Tag(name = "Роли")
    @Operation(summary = "Получить список ролей")
    public List<RoleDto> roles() {
        return roleService.findAll().stream().map(CatalogApiService::toDto).toList();
    }

    @GetMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Tag(name = "Роли")
    @Operation(summary = "Получить роль по ID")
    public RoleDto role(@PathVariable Integer id) {
        return toDto(require(roleService.findById(id)));
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Tag(name = "Роли")
    @Operation(summary = "Создать роль")
    public RoleDto createRole(@Valid @RequestBody RoleDto request) {
        Role value = new Role();
        value.setRoleName(request.roleName());
        value.setDescription(request.description());
        if (request.specialtyId() != null) {
            value.setSpecialty(require(specialtyService.findById(request.specialtyId())));
        }
        return toDto(roleService.save(value));
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Tag(name = "Роли")
    @Operation(summary = "Изменить роль")
    public RoleDto updateRole(@PathVariable Integer id, @Valid @RequestBody RoleDto request) {
        Role value = require(roleService.findById(id));
        value.setRoleName(request.roleName());
        value.setDescription(request.description());
        value.setSpecialty(request.specialtyId() == null ? null : require(specialtyService.findById(request.specialtyId())));
        return toDto(roleService.save(value));
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Tag(name = "Роли")
    @Operation(summary = "Удалить роль")
    public void deleteRole(@PathVariable Integer id) {
        require(roleService.findById(id));
        roleService.deleteById(id);
    }

    @GetMapping("/specialties")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Tag(name = "Специальности")
    @Operation(summary = "Получить список специальностей")
    public List<SpecialtyDto> specialties() {
        return specialtyService.findAll().stream().map(CatalogApiService::toDto).toList();
    }

    @GetMapping("/specialties/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Tag(name = "Специальности")
    @Operation(summary = "Получить специальность по ID")
    public SpecialtyDto specialty(@PathVariable Integer id) {
        return toDto(require(specialtyService.findById(id)));
    }

    @PostMapping("/specialties")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Tag(name = "Специальности")
    @Operation(summary = "Создать специальность")
    public SpecialtyDto createSpecialty(@Valid @RequestBody SpecialtyDto request) {
        Specialty value = new Specialty();
        value.setSpecialtyName(request.specialtyName());
        value.setDescription(request.description());
        return toDto(specialtyService.save(value));
    }

    @PutMapping("/specialties/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Tag(name = "Специальности")
    @Operation(summary = "Изменить специальность")
    public SpecialtyDto updateSpecialty(@PathVariable Integer id, @Valid @RequestBody SpecialtyDto request) {
        Specialty value = require(specialtyService.findById(id));
        value.setSpecialtyName(request.specialtyName());
        value.setDescription(request.description());
        return toDto(specialtyService.save(value));
    }

    @DeleteMapping("/specialties/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Tag(name = "Специальности")
    @Operation(summary = "Удалить специальность")
    public void deleteSpecialty(@PathVariable Integer id) {
        require(specialtyService.findById(id));
        specialtyService.deleteById(id);
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Tag(name = "Пользователи")
    @Operation(summary = "Получить список пользователей без паролей")
    public List<UserDto> users() {
        return userService.findAll().stream().map(CatalogApiService::toDto).toList();
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Tag(name = "Пользователи")
    @Operation(summary = "Получить пользователя по ID без пароля")
    public UserDto user(@PathVariable Integer id) {
        return toDto(require(userService.findById(id)));
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Tag(name = "Пользователи")
    @Operation(summary = "Создать пользователя")
    public UserDto createUser(@Valid @RequestBody UserCreateRequest request) {
        User value = new User();
        value.setUsername(request.username());
        value.setPassword(request.password());
        value.setActive(request.active());
        value.setSurname(request.surname());
        value.setFirstName(request.firstName());
        value.setMiddleName(request.middleName());
        value.setBirthDate(request.birthDate());
        value.setPhone(request.phone());
        value.setAddress(request.address());
        value.setPolicyNumber(request.policyNumber());
        value.setEmail(request.email());
        value.setRoles(request.roles());
        if (request.departmentId() != null) {
            value.setDepartment(require(departmentService.findById(request.departmentId())));
        }
        if (request.specialtyId() != null) {
            value.setSpecialty(require(specialtyService.findById(request.specialtyId())));
        }
        return toDto(userService.save(value));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Tag(name = "Пользователи")
    @Operation(summary = "Изменить пользователя")
    public UserDto updateUser(@PathVariable Integer id, @Valid @RequestBody UserCreateRequest request) {
        User value = require(userService.findById(id));
        value.setUsername(request.username());
        if (request.password() != null && !request.password().isBlank()) {
            value.setPassword(request.password());
        }
        value.setActive(request.active());
        value.setSurname(request.surname());
        value.setFirstName(request.firstName());
        value.setMiddleName(request.middleName());
        value.setBirthDate(request.birthDate());
        value.setPhone(request.phone());
        value.setAddress(request.address());
        value.setPolicyNumber(request.policyNumber());
        value.setEmail(request.email());
        value.setRoles(request.roles());
        value.setDepartment(request.departmentId() == null ? null : require(departmentService.findById(request.departmentId())));
        value.setSpecialty(request.specialtyId() == null ? null : require(specialtyService.findById(request.specialtyId())));
        return toDto(userService.save(value));
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Tag(name = "Пользователи")
    @Operation(summary = "Удалить пользователя")
    public void deleteUser(@PathVariable Integer id) {
        require(userService.findById(id));
        userService.deleteById(id);
    }

    private static <T> T require(T value) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Запись не найдена");
        }
        return value;
    }

    private List<PrescriptionDto> visiblePrescriptions(org.springframework.security.core.Authentication auth) {
        if (hasAuthority(auth, "PATIENT") && !hasAuthority(auth, "DOCTOR") && !hasAuthority(auth, "ADMIN")) {
            User patient = userService.findByUsername(auth.getName());
            return appointmentService.findByUser(patient.getIdUser()).stream()
                    .flatMap(a -> medicalRecordService.findByAppointment(a.getIdAppointment()).stream())
                    .flatMap(r -> prescriptionService.findByMedicalRecord(r.getIdRecord()).stream())
                    .map(CatalogApiService::toDto).toList();
        }
        if (hasAuthority(auth, "DOCTOR") && !hasAuthority(auth, "ADMIN")) {
            User doctor = userService.findByUsername(auth.getName());
            return appointmentService.findByDoctor(doctor.getIdUser()).stream()
                    .flatMap(a -> medicalRecordService.findByAppointment(a.getIdAppointment()).stream())
                    .flatMap(r -> prescriptionService.findByMedicalRecord(r.getIdRecord()).stream())
                    .map(CatalogApiService::toDto).toList();
        }
        return prescriptionService.findAll().stream().map(CatalogApiService::toDto).toList();
    }

    private void ensureMedicalRecordAccess(MedicalRecord record, org.springframework.security.core.Authentication auth) {
        Appointment appointment = record.getAppointment();
        ensureAppointmentAccess(appointment, auth);
    }

    private void ensureAppointmentAccess(Appointment appointment, org.springframework.security.core.Authentication auth) {
        if (appointment == null || auth == null) throw new org.springframework.security.access.AccessDeniedException("Доступ запрещён");
        User actor = userService.findByUsername(auth.getName());
        boolean allowed = hasAuthority(auth, "DOCTOR") && appointment.getDoctor() != null
                && actor.getIdUser().equals(appointment.getDoctor().getIdUser())
                || hasAuthority(auth, "PATIENT") && appointment.getUser() != null
                && actor.getIdUser().equals(appointment.getUser().getIdUser());
        if (!allowed) throw new org.springframework.security.access.AccessDeniedException("Доступ запрещён");
    }

    private void requireAssignedDoctor(org.springframework.security.core.Authentication auth) {
        if (!hasAuthority(auth, "DOCTOR") || hasAuthority(auth, "ADMIN")) {
            throw new org.springframework.security.access.AccessDeniedException("Доступно только лечащему врачу");
        }
    }

    private static boolean hasAuthority(org.springframework.security.core.Authentication auth, String authority) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }

    private static DepartmentDto toDto(Department value) {
        return new DepartmentDto(value.getIdDepartment(), value.getDepartmentName(), value.getFloor(), value.getRoomNumber());
    }

    private static MedicalRecordDto toDto(MedicalRecord value) {
        return new MedicalRecordDto(value.getIdRecord(), value.getDiagnosis(), value.getTreatment(), value.getRecommendations(),
                value.getCreatedDate(), value.getAppointment() == null ? null : value.getAppointment().getIdAppointment());
    }

    private static ServiceDto toDto(MedicalServiceEntity value) {
        return new ServiceDto(value.getIdService(), value.getServiceName(), value.getCost(), value.getDescription());
    }

    private static PrescriptionDto toDto(Prescription value) {
        return new PrescriptionDto(value.getIdPrescription(), value.getMedicationName(), value.getDosage(), value.getFrequency(),
                value.getDurationDays(), value.getMedicalRecord() == null ? null : value.getMedicalRecord().getIdRecord(),
                value.getService() == null ? null : value.getService().getIdService());
    }

    private static RoleDto toDto(Role value) {
        return new RoleDto(value.getIdRole(), value.getRoleName(), value.getDescription(),
                value.getSpecialty() == null ? null : value.getSpecialty().getIdSpecialty());
    }

    private static SpecialtyDto toDto(Specialty value) {
        return new SpecialtyDto(value.getIdSpecialty(), value.getSpecialtyName(), value.getDescription());
    }

    private static UserDto toDto(User value) {
        return new UserDto(value.getIdUser(), value.getUsername(), value.isActive(), value.getSurname(), value.getFirstName(),
                value.getMiddleName(), value.getBirthDate(), value.getPhone(), value.getAddress(), value.getPolicyNumber(), value.getEmail(),
                value.getIdRole(), value.getRoles(), value.getDepartment() == null ? null : value.getDepartment().getIdDepartment(),
                value.getSpecialty() == null ? null : value.getSpecialty().getIdSpecialty());
    }

    public record DepartmentDto(Integer id, String departmentName, Integer floor, String roomNumber) { }
    public record MedicalRecordDto(Integer id, String diagnosis, String treatment, String recommendations,
                                   LocalDateTime createdDate, Integer appointmentId) { }
    public record ServiceDto(Integer id, String serviceName, Integer cost, String description) { }
    public record PrescriptionDto(Integer id, String medicationName, String dosage, String frequency,
                                  Integer durationDays, Integer medicalRecordId, Integer serviceId) { }
    public record RoleDto(Integer id, String roleName, String description, Integer specialtyId) { }
    public record SpecialtyDto(Integer id, String specialtyName, String description) { }
    public record UserDto(Integer id, String username, boolean active, String surname, String firstName,
                          String middleName, LocalDate birthDate, String phone, String address, String policyNumber,
                          String email, Integer idRole, Set<RoleEnum> roles, Integer departmentId, Integer specialtyId) { }
    public record UserCreateRequest(String username, String password, boolean active, String surname, String firstName,
                                    String middleName, LocalDate birthDate, String phone, String address, String policyNumber,
                                    String email, Set<RoleEnum> roles, Integer departmentId, Integer specialtyId) { }
}
