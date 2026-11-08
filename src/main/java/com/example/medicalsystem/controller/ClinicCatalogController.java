package com.example.medicalsystem.controller;

import com.example.medicalsystem.model.AppointmentGoal;
import com.example.medicalsystem.model.ClinicBranch;
import com.example.medicalsystem.model.Specialty;
import com.example.medicalsystem.repository.AppointmentGoalRepository;
import com.example.medicalsystem.repository.ClinicBranchRepository;
import com.example.medicalsystem.repository.SpecialtyRepository;
import com.example.medicalsystem.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Controller
@RequestMapping("/admin/clinic-catalog")
@PreAuthorize("hasAuthority('ADMIN')")
public class ClinicCatalogController {
    private final AppointmentGoalRepository goals;
    private final ClinicBranchRepository branches;
    private final SpecialtyRepository specialties;
    private final UserRepository users;

    public ClinicCatalogController(AppointmentGoalRepository goals, ClinicBranchRepository branches,
                                   SpecialtyRepository specialties, UserRepository users) {
        this.goals = goals; this.branches = branches; this.specialties = specialties; this.users = users;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public String catalog(Model model) {
        model.addAttribute("goals", goals.findAllByOrderBySortOrderAscNameAsc());
        model.addAttribute("branches", branches.findAllByOrderByNameAsc());
        model.addAttribute("specialties", specialties.findAll());
        model.addAttribute("doctors", users.findAll().stream().filter(user -> user.getRoles() != null
                && user.getRoles().contains(com.example.medicalsystem.model.RoleEnum.DOCTOR)).toList());
        model.addAttribute("groups", AppointmentGoal.GoalGroup.values());
        model.addAttribute("scenarios", AppointmentGoal.ScenarioType.values());
        return "adminClinicCatalog";
    }

    @PostMapping("/goals/save")
    @Transactional
    public String saveGoal(@RequestParam(required = false) Integer id,
                           @RequestParam String name, @RequestParam String iconKey,
                           @RequestParam AppointmentGoal.GoalGroup goalGroup,
                           @RequestParam AppointmentGoal.ScenarioType scenarioType,
                           @RequestParam(defaultValue = "0") int sortOrder,
                           @RequestParam(defaultValue = "false") boolean paid,
                           @RequestParam(defaultValue = "true") boolean active,
                           @RequestParam(required = false) String description,
                           @RequestParam(required = false) String externalUrl,
                           @RequestParam(name = "specialtyIds", required = false) List<Integer> specialtyIds) {
        AppointmentGoal goal = id == null ? new AppointmentGoal() : goals.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Цель обращения не найдена."));
        goal.setName(name.trim()); goal.setIconKey(iconKey.trim()); goal.setGoalGroup(goalGroup);
        goal.setScenarioType(scenarioType); goal.setSortOrder(sortOrder); goal.setPaid(paid);
        goal.setActive(active); goal.setDescription(description); goal.setExternalUrl(externalUrl);
        goal.setSpecialties(specialtyIds == null ? List.of() : specialtyIds.stream().distinct()
                .map(specialtyId -> specialties.findById(specialtyId).orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.BAD_REQUEST, "Специальность не найдена."))).toList());
        goals.save(goal);
        return "redirect:/admin/clinic-catalog";
    }

    @PostMapping("/goals/{id}/toggle")
    @Transactional
    public String toggleGoal(@PathVariable Integer id) {
        AppointmentGoal goal = goals.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Цель обращения не найдена."));
        goal.setActive(!goal.isActive()); goals.save(goal);
        return "redirect:/admin/clinic-catalog";
    }

    @PostMapping("/branches/save")
    @Transactional
    public String saveBranch(@RequestParam(required = false) Integer id,
                             @RequestParam String name, @RequestParam String address,
                             @RequestParam(required = false) String phone,
                             @RequestParam(name = "doctorIds", required = false) List<Integer> doctorIds) {
        ClinicBranch branch = id == null ? new ClinicBranch() : branches.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Филиал не найден."));
        branch.setName(name.trim()); branch.setAddress(address.trim()); branch.setPhone(phone);
        final ClinicBranch savedBranch = branches.save(branch);
        List<Integer> selectedIds = doctorIds == null ? List.of() : doctorIds.stream().distinct().toList();
        for (var doctor : users.findAll()) {
            boolean isDoctor = doctor.getRoles() != null && doctor.getRoles().stream().anyMatch(role -> role.name().equals("DOCTOR"));
            if (!isDoctor) continue;
            List<ClinicBranch> assigned = doctor.getBranches() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(doctor.getBranches());
            boolean selected = selectedIds.contains(doctor.getIdUser());
            if (selected && assigned.stream().noneMatch(item -> item.getId().equals(savedBranch.getId()))) assigned.add(savedBranch);
            if (!selected) assigned.removeIf(item -> item.getId().equals(savedBranch.getId()));
            doctor.setBranches(assigned); users.save(doctor);
        }
        return "redirect:/admin/clinic-catalog";
    }
}
