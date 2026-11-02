package com.example.medicalsystem.controller;

import com.example.medicalsystem.model.Specialty;
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
@RequestMapping("/specialties")
@PreAuthorize("hasAuthority('ADMIN')")
@Tag(name = "Специальности", description = "Медицинские специальности")
public class SpecialtyController {

    private final SpecialtyService specialtyService;

    public SpecialtyController(SpecialtyService specialtyService) {
        this.specialtyService = specialtyService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page,
                       @RequestParam(required = false) String searchField,
                       @RequestParam(required = false) String searchValue,
                       Model model) {
        Page<Specialty> specialties = specialtyService.search(searchField, searchValue, page);

        model.addAttribute("specialties", specialties.getContent());
        addPagination(model, specialties);
        model.addAttribute("searchField", searchField);
        model.addAttribute("searchValue", searchValue);

        return "specialties";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("specialty", new Specialty());
        return "specialtyForm";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("specialty") Specialty specialty,
                       BindingResult result,
                       Model model) {
        if (result.hasErrors()) {
            model.addAttribute("specialty", specialty);
            return "specialtyForm";
        }
        specialtyService.save(specialty);
        return "redirect:/specialties";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Integer id, Model model) {
        model.addAttribute("specialty", specialtyService.findById(id));
        return "specialtyForm";
    }

    @PostMapping("/update")
    public String update(@RequestParam Integer id,
                         @Valid @ModelAttribute("specialty") Specialty specialty,
                         BindingResult result,
                         Model model) {
        if (result.hasErrors()) {
            specialty.setIdSpecialty(id);
            model.addAttribute("specialty", specialty);
            return "specialtyForm";
        }
        specialty.setIdSpecialty(id);
        specialtyService.save(specialty);
        return "redirect:/specialties";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Integer id) {
        specialtyService.deleteById(id);
        return "redirect:/specialties";
    }

    private void addPagination(Model model, Page<?> page) {
        model.addAttribute("currentPage", page.getNumber() + 1);
        model.addAttribute("totalPages", Math.max(page.getTotalPages(), 1));
        model.addAttribute("hasNext", page.hasNext());
        model.addAttribute("hasPrevious", page.hasPrevious());
    }
}
