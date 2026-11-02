package com.example.medicalsystem.controller;

import com.example.medicalsystem.model.Role;
import com.example.medicalsystem.service.RoleService;
import com.example.medicalsystem.service.SpecialtyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/roles")
@PreAuthorize("hasAuthority('ADMIN')")
@Tag(name = "Роли", description = "Справочник ролей пользователей")
public class RoleController {

    private final RoleService roleService;
    private final SpecialtyService specialtyService;

    public RoleController(RoleService roleService, SpecialtyService specialtyService) {
        this.roleService = roleService;
        this.specialtyService = specialtyService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page,
                       @RequestParam(required = false) String searchField,
                       @RequestParam(required = false) String searchValue,
                       Model model) {
        Page<Role> roles = roleService.search(searchField, searchValue, page);

        model.addAttribute("roles", roles.getContent());
        addPagination(model, roles);
        model.addAttribute("searchField", searchField);
        model.addAttribute("searchValue", searchValue);

        return "roles";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("role", new Role());
        model.addAttribute("specialties", specialtyService.findAll());
        return "roleForm";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("role") Role role,
                       BindingResult result,
                       Model model) {
        if (result.hasErrors()) {
            model.addAttribute("role", role);
            model.addAttribute("specialties", specialtyService.findAll());
            return "roleForm";
        }
        roleService.save(role);
        return "redirect:/roles";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Integer id, Model model) {
        model.addAttribute("role", roleService.findById(id));
        model.addAttribute("specialties", specialtyService.findAll());
        return "roleForm";
    }

    @PostMapping("/update")
    public String update(@RequestParam Integer id,
                         @Valid @ModelAttribute("role") Role role,
                         BindingResult result,
                         Model model) {
        if (result.hasErrors()) {
            role.setIdRole(id);
            model.addAttribute("role", role);
            model.addAttribute("specialties", specialtyService.findAll());
            return "roleForm";
        }
        role.setIdRole(id);
        roleService.save(role);
        return "redirect:/roles";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Integer id) {
        roleService.deleteById(id);
        return "redirect:/roles";
    }

    private void addPagination(Model model, Page<?> page) {
        model.addAttribute("currentPage", page.getNumber() + 1);
        model.addAttribute("totalPages", Math.max(page.getTotalPages(), 1));
        model.addAttribute("hasNext", page.hasNext());
        model.addAttribute("hasPrevious", page.hasPrevious());
    }
}
