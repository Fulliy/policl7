package com.example.medicalsystem.controller;

import com.example.medicalsystem.model.ClinicBranch;
import com.example.medicalsystem.model.RoleEnum;
import com.example.medicalsystem.repository.ClinicBranchRepository;
import com.example.medicalsystem.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/admin/clinic-catalog")
@PreAuthorize("hasAnyAuthority('ADMIN', 'ROLE_ADMIN')")
public class ClinicCatalogController {
    private final ClinicBranchRepository branches;
    private final UserRepository users;

    public ClinicCatalogController(ClinicBranchRepository branches, UserRepository users) {
        this.branches = branches;
        this.users = users;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public String catalog(Model model) {
        model.addAttribute("branches", branches.findAllByOrderByNameAsc());
        model.addAttribute("doctors", users.findAll().stream()
                .filter(user -> user.getRoles() != null && user.getRoles().contains(RoleEnum.DOCTOR))
                .toList());
        return "adminClinicCatalog";
    }

    @PostMapping("/branches/save")
    @Transactional
    public String saveBranch(@RequestParam(required = false) Integer id,
                             @RequestParam String name,
                             @RequestParam String address,
                             @RequestParam(required = false) String phone,
                             @RequestParam(name = "doctorIds", required = false) List<Integer> doctorIds) {
        ClinicBranch branch = id == null ? new ClinicBranch() : branches.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Филиал не найден."));
        branch.setName(name.trim());
        branch.setAddress(address.trim());
        branch.setPhone(phone == null || phone.isBlank() ? null : phone.trim());
        ClinicBranch savedBranch = branches.save(branch);

        List<Integer> selectedIds = doctorIds == null ? List.of() : doctorIds.stream().distinct().toList();
        for (var doctor : users.findAll()) {
            if (doctor.getRoles() == null || !doctor.getRoles().contains(RoleEnum.DOCTOR)) continue;
            List<ClinicBranch> assigned = doctor.getBranches() == null
                    ? new ArrayList<>() : new ArrayList<>(doctor.getBranches());
            boolean selected = selectedIds.contains(doctor.getIdUser());
            boolean alreadyAssigned = assigned.stream().anyMatch(item -> item.getId().equals(savedBranch.getId()));
            if (selected && !alreadyAssigned) assigned.add(savedBranch);
            if (!selected) assigned.removeIf(item -> item.getId().equals(savedBranch.getId()));
            doctor.setBranches(assigned);
            users.save(doctor);
        }
        return "redirect:/admin/clinic-catalog";
    }
}
