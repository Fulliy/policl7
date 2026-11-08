package com.example.medicalsystem.controller;

import com.example.medicalsystem.model.User;
import com.example.medicalsystem.model.RoleEnum;
import com.example.medicalsystem.service.DepartmentService;
import com.example.medicalsystem.service.SpecialtyService;
import com.example.medicalsystem.service.UserService;
import com.example.medicalsystem.repository.ClinicBranchRepository;
import com.example.medicalsystem.repository.DepartmentRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

@Controller
@RequestMapping("/users")
@PreAuthorize("hasAuthority('ADMIN')")
@Tag(name = "Пользователи", description = "Управление пользователями системы")
public class UserController {

    private final UserService service;
    private final DepartmentService departmentService;
    private final SpecialtyService specialtyService;
    private final ClinicBranchRepository branchRepository;
    private final DepartmentRepository departments;

    public UserController(UserService service,
                          DepartmentService departmentService,
                          SpecialtyService specialtyService,
                          ClinicBranchRepository branchRepository,
                          DepartmentRepository departments) {
        this.service = service;
        this.departmentService = departmentService;
        this.specialtyService = specialtyService;
        this.branchRepository = branchRepository;
        this.departments = departments;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page,
                       @RequestParam(required = false) String searchField,
                       @RequestParam(required = false) String searchValue,
                       Model model) {
        Page<User> userPage = service.search(searchField, searchValue, page);

        model.addAttribute("users", userPage.getContent());
        addPagination(model, userPage);
        model.addAttribute("searchField", searchField);
        model.addAttribute("searchValue", searchValue);

        return "users";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("user", new User());
        model.addAttribute("roles", RoleEnum.values());
        addRelationLists(model);
        return "userForm";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("user") User user,
                       @RequestParam(name = "doctorDepartmentIds", required = false) List<Integer> doctorDepartmentIds,
                       @RequestParam(name = "branchIds", required = false) List<Integer> branchIds,
                       @RequestParam(name = "roles", required = false) List<RoleEnum> roles,
                       BindingResult result,
                       Model model) {
        if (roles != null) user.setRoles(new java.util.HashSet<>(roles));
        if (result.hasErrors()) {
            model.addAttribute("user", user);
            model.addAttribute("roles", RoleEnum.values());
            addRelationLists(model);
            return "userForm";
        }
        try {
            assignDoctorLocations(user, doctorDepartmentIds, branchIds);
            service.save(user);
        } catch (Exception ex) {
            model.addAttribute("returnUrl", "/admin/registry?tab=patients");
            model.addAttribute("user", user);
            model.addAttribute("roles", RoleEnum.values());
            addRelationLists(model);
            model.addAttribute("saveError", "Не удалось сохранить пользователя. Проверьте данные и попробуйте еще раз.");
            return "userForm";
        }
        return "redirect:/users";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Integer id,
                               @RequestParam(defaultValue = "patients") String section,
                               Model model) {
        model.addAttribute("user", service.findById(id));
        model.addAttribute("roles", RoleEnum.values());
        model.addAttribute("returnUrl", "/admin/registry?tab=" + ("doctors".equals(section) ? "doctors" : "patients"));
        addRelationLists(model);
        return "userForm";
    }

    @PostMapping("/update")
    public String update(@RequestParam Integer id,
                         @RequestParam(defaultValue = "patients") String section,
                         @Valid @ModelAttribute("user") User user,
                         @RequestParam(name = "doctorDepartmentIds", required = false) List<Integer> doctorDepartmentIds,
                         @RequestParam(name = "branchIds", required = false) List<Integer> branchIds,
                         @RequestParam(name = "roles", required = false) List<RoleEnum> roles,
                         BindingResult result,
                         Model model) {
        if (roles != null) user.setRoles(new java.util.HashSet<>(roles));
        if (result.hasErrors()) {
            user.setIdUser(id);
            model.addAttribute("returnUrl", "/admin/registry?tab=" + ("doctors".equals(section) ? "doctors" : "patients"));
            model.addAttribute("user", user);
            model.addAttribute("roles", RoleEnum.values());
            addRelationLists(model);
            return "userForm";
        }
        user.setIdUser(id);
        try {
            assignDoctorLocations(user, doctorDepartmentIds, branchIds);
            service.save(user);
        } catch (Exception ex) {
            model.addAttribute("returnUrl", "/admin/registry?tab=" + ("doctors".equals(section) ? "doctors" : "patients"));
            model.addAttribute("user", user);
            model.addAttribute("roles", RoleEnum.values());
            addRelationLists(model);
            model.addAttribute("saveError", "Не удалось сохранить пользователя. Проверьте данные и попробуйте еще раз.");
            return "userForm";
        }
        return "redirect:/admin/registry?tab=" + ("doctors".equals(section) ? "doctors" : "patients");
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Integer id) {
        service.deleteById(id);
        return "redirect:/users";
    }

    private void addPagination(Model model, Page<?> page) {
        model.addAttribute("currentPage", page.getNumber() + 1);
        model.addAttribute("totalPages", Math.max(page.getTotalPages(), 1));
        model.addAttribute("hasNext", page.hasNext());
        model.addAttribute("hasPrevious", page.hasPrevious());
    }

    private void addRelationLists(Model model) {
        model.addAttribute("departments", departmentService.findAll());
        model.addAttribute("specialties", specialtyService.findAll());
        model.addAttribute("branches", branchRepository.findAllByOrderByNameAsc());
    }

    private void assignDoctorLocations(User user, List<Integer> departmentIds, List<Integer> branchIds) {
        if (user.getRoles() == null || !user.getRoles().contains(com.example.medicalsystem.model.RoleEnum.DOCTOR)) return;
        user.setDoctorDepartments(departmentIds == null ? List.of() : departmentIds.stream().distinct()
                .map(id -> departments.findById(id).orElseThrow()).toList());
        user.setBranches(branchIds == null ? List.of() : branchIds.stream().distinct()
                .map(id -> branchRepository.findById(id).orElseThrow()).toList());
        if (user.getDepartment() == null && !user.getDoctorDepartments().isEmpty()) {
            user.setDepartment(user.getDoctorDepartments().get(0));
        }
    }
}
