package com.example.medicalsystem.controller;

import com.example.medicalsystem.model.User;
import com.example.medicalsystem.service.DepartmentService;
import com.example.medicalsystem.service.SpecialtyService;
import com.example.medicalsystem.service.UserService;
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

@Controller
@RequestMapping("/users")
@PreAuthorize("hasAuthority('ADMIN')")
@Tag(name = "Пользователи", description = "Управление пользователями системы")
public class UserController {

    private final UserService service;
    private final DepartmentService departmentService;
    private final SpecialtyService specialtyService;

    public UserController(UserService service,
                          DepartmentService departmentService,
                          SpecialtyService specialtyService) {
        this.service = service;
        this.departmentService = departmentService;
        this.specialtyService = specialtyService;
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
        addRelationLists(model);
        return "userForm";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("user") User user,
                       BindingResult result,
                       Model model) {
        if (result.hasErrors()) {
            model.addAttribute("user", user);
            addRelationLists(model);
            return "userForm";
        }
        try {
            service.save(user);
        } catch (Exception ex) {
            model.addAttribute("user", user);
            addRelationLists(model);
            model.addAttribute("saveError", "Не удалось сохранить пользователя. Проверьте данные и попробуйте еще раз.");
            return "userForm";
        }
        return "redirect:/users";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Integer id, Model model) {
        model.addAttribute("user", service.findById(id));
        addRelationLists(model);
        return "userForm";
    }

    @PostMapping("/update")
    public String update(@RequestParam Integer id,
                         @Valid @ModelAttribute("user") User user,
                         BindingResult result,
                         Model model) {
        if (result.hasErrors()) {
            user.setIdUser(id);
            model.addAttribute("user", user);
            addRelationLists(model);
            return "userForm";
        }
        user.setIdUser(id);
        try {
            service.save(user);
        } catch (Exception ex) {
            model.addAttribute("user", user);
            addRelationLists(model);
            model.addAttribute("saveError", "Не удалось сохранить пользователя. Проверьте данные и попробуйте еще раз.");
            return "userForm";
        }
        return "redirect:/users";
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
    }
}
