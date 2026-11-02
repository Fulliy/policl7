package com.example.medicalsystem.controller;

import com.example.medicalsystem.model.Department;
import com.example.medicalsystem.service.DepartmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/departments")
@PreAuthorize("hasAuthority('ADMIN')")
@Tag(name = "Отделения", description = "Управление отделениями поликлиники")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page,
                       @RequestParam(required = false) String searchField,
                       @RequestParam(required = false) String searchValue,
                       Model model) {
        Page<Department> departments = departmentService.search(searchField, searchValue, page);

        model.addAttribute("departments", departments.getContent());
        addPagination(model, departments);
        model.addAttribute("searchField", searchField);
        model.addAttribute("searchValue", searchValue);

        return "departments";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("department", new Department());
        return "departmentForm";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("department") Department department,
                       BindingResult result,
                       Model model) {
        if (result.hasErrors()) {
            model.addAttribute("department", department);
            return "departmentForm";
        }
        departmentService.save(department);
        return "redirect:/departments";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Integer id, Model model) {
        model.addAttribute("department", departmentService.findById(id));
        return "departmentForm";
    }

    @PostMapping("/update")
    public String update(@RequestParam Integer id,
                         @Valid @ModelAttribute("department") Department department,
                         BindingResult result,
                         Model model) {
        if (result.hasErrors()) {
            department.setIdDepartment(id);
            model.addAttribute("department", department);
            return "departmentForm";
        }
        department.setIdDepartment(id);
        departmentService.save(department);
        return "redirect:/departments";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Integer id) {
        departmentService.deleteById(id);
        return "redirect:/departments";
    }

    private void addPagination(Model model, Page<?> page) {
        model.addAttribute("currentPage", page.getNumber() + 1);
        model.addAttribute("totalPages", Math.max(page.getTotalPages(), 1));
        model.addAttribute("hasNext", page.hasNext());
        model.addAttribute("hasPrevious", page.hasPrevious());
    }
}
