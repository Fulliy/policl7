package com.example.medicalsystem.service;

import com.example.medicalsystem.model.Appointment;
import com.example.medicalsystem.repository.AppointmentRepository;
import com.example.medicalsystem.repository.DepartmentRepository;
import com.example.medicalsystem.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class AppointmentService {

    private final AppointmentRepository repository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    public AppointmentService(AppointmentRepository repository,
                              UserRepository userRepository,
                              DepartmentRepository departmentRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
    }

    @PostConstruct
    public void init() {
        long count = repository.count();
        for (int i = (int) count + 1; i <= 10; i++) {
            Appointment appointment = new Appointment();
            appointment.setAppointmentDate(LocalDateTime.now().plusDays(i));
            appointment.setStatus("Запланирован");
            appointment.setComplaint("Жалоба " + i);
            appointment.setUser(userRepository.findById(1).orElse(null));
            appointment.setDepartment(departmentRepository.findById(1).orElse(null));
            repository.save(appointment);
        }
    }

    public List<Appointment> findAll() {
        return repository.findAll();
    }

    public Page<Appointment> findPage(int page) {
        return repository.findAll(PaginationHelper.pageable(page));
    }

    public Appointment findById(Integer id) {
        return repository.findById(id).orElse(null);
    }

    public Appointment save(Appointment appointment) {
        return repository.save(appointment);
    }

    public boolean isDoctorAvailable(Integer doctorId, LocalDateTime appointmentDate, Integer excludeAppointmentId) {
        if (doctorId == null || appointmentDate == null) return true;
        return repository.findByDoctor_IdUserAndAppointmentDate(doctorId, appointmentDate).stream()
                .noneMatch(existing -> !existing.getIdAppointment().equals(excludeAppointmentId)
                        && !"Отменен".equalsIgnoreCase(existing.getStatus()));
    }

    public void deleteById(Integer id) {
        repository.deleteById(id);
    }

    public Page<Appointment> search(String fieldName, String value, int page) {
        if (fieldName == null || value == null || value.isBlank()) {
            return findPage(page);
        }

        String trimmedValue = value.trim();
        if ("id".equals(fieldName) || "idAppointment".equals(fieldName)) {
            return parseId(trimmedValue)
                    .map(id -> repository.findById(id).map(List::of).orElseGet(List::of))
                    .map(items -> PaginationHelper.pageFromList(items, page))
                    .orElseGet(() -> PaginationHelper.pageFromList(List.of(), page));
        }

        if ("name".equals(fieldName) || "status".equals(fieldName)) {
            return PaginationHelper.pageFromList(repository.findByStatus(trimmedValue), page);
        }

        return PaginationHelper.pageFromList(repository.searchByKeyword(trimmedValue), page);
    }

    public Page<Appointment> searchAdmin(String fieldName, String value, String status, int page) {
        List<Appointment> appointments = repository.findAll();
        if (value != null && !value.isBlank()) {
            String query = value.trim().toLowerCase();
            appointments = appointments.stream().filter(appointment -> switch (fieldName == null ? "all" : fieldName) {
                case "patient" -> appointment.getUser() != null
                        && appointment.getUser().getDisplayName().toLowerCase().contains(query);
                case "doctor" -> appointment.getDoctor() != null
                        && appointment.getDoctor().getDisplayName().toLowerCase().contains(query);
                case "department" -> appointment.getDepartment() != null
                        && appointment.getDepartment().getDepartmentName().toLowerCase().contains(query);
                default -> (appointment.getUser() != null
                        && appointment.getUser().getDisplayName().toLowerCase().contains(query))
                        || (appointment.getDoctor() != null
                        && appointment.getDoctor().getDisplayName().toLowerCase().contains(query))
                        || (appointment.getDepartment() != null
                        && appointment.getDepartment().getDepartmentName().toLowerCase().contains(query))
                        || (appointment.getComplaint() != null
                        && appointment.getComplaint().toLowerCase().contains(query));
            }).toList();
        }
        if (status != null && !status.isBlank()) {
            appointments = appointments.stream()
                    .filter(appointment -> appointment.getStatus() != null
                            && appointment.getStatus().equalsIgnoreCase(status))
                    .toList();
        }
        return PaginationHelper.pageFromList(appointments, page);
    }

    public Page<Appointment> searchForUser(Integer userId, String fieldName, String value, int page) {
        return searchForUser(userId, fieldName, value, page, null);
    }

    public Page<Appointment> searchForUser(Integer userId, String fieldName, String value, int page,
                                           String visitPeriod) {
        List<Appointment> appointments = repository.findByUser_IdUser(userId);
        if (fieldName != null && value != null && !value.isBlank()) {
            String query = value.trim();
            if ("id".equals(fieldName) || "idAppointment".equals(fieldName)) {
                appointments = parseId(query)
                        .flatMap(repository::findById)
                        .filter(appointment -> appointment.getUser() != null
                                && userId.equals(appointment.getUser().getIdUser()))
                        .map(List::of)
                        .orElseGet(List::of);
            } else if ("name".equals(fieldName) || "status".equals(fieldName)) {
                appointments = appointments.stream()
                        .filter(appointment -> appointment.getStatus() != null
                                && appointment.getStatus().equalsIgnoreCase(query))
                        .toList();
            } else if ("doctor".equals(fieldName)) {
                String lowerQuery = query.toLowerCase();
                appointments = appointments.stream()
                        .filter(appointment -> appointment.getDoctor() != null
                                && ((appointment.getDoctor().getDisplayName() != null
                                && appointment.getDoctor().getDisplayName().toLowerCase().contains(lowerQuery))
                                || (appointment.getDoctor().getSpecialty() != null
                                && appointment.getDoctor().getSpecialty().getSpecialtyName() != null
                                && appointment.getDoctor().getSpecialty().getSpecialtyName()
                                .toLowerCase().contains(lowerQuery))))
                        .toList();
            } else {
                String lowerQuery = query.toLowerCase();
                appointments = appointments.stream()
                        .filter(appointment -> (appointment.getComplaint() != null
                                && appointment.getComplaint().toLowerCase().contains(lowerQuery))
                                || (appointment.getStatus() != null
                                && appointment.getStatus().toLowerCase().contains(lowerQuery)))
                        .toList();
            }
        }
        LocalDateTime now = LocalDateTime.now();
        if ("past".equals(visitPeriod)) {
            appointments = appointments.stream()
                    .filter(appointment -> appointment.getAppointmentDate() != null
                            && appointment.getAppointmentDate().isBefore(now))
                    .toList();
        } else if ("upcoming".equals(visitPeriod)) {
            appointments = appointments.stream()
                    .filter(appointment -> appointment.getAppointmentDate() == null
                            || !appointment.getAppointmentDate().isBefore(now))
                    .toList();
        }
        return PaginationHelper.pageFromList(appointments, page);
    }

    public List<Appointment> findByStatus(String status) {
        return repository.findByStatus(status);
    }

    public List<Appointment> findByUser(Integer idUser) {
        return repository.findByUser_IdUser(idUser);
    }

    public List<Appointment> findByDoctor(Integer doctorId) {
        return repository.findByDoctor_IdUser(doctorId);
    }

    public List<Appointment> findByDateRange(LocalDateTime start, LocalDateTime end) {
        return repository.findByAppointmentDateBetween(start, end);
    }

    private Optional<Integer> parseId(String value) {
        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
