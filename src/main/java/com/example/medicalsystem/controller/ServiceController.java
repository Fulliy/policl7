package com.example.medicalsystem.controller;

import com.example.medicalsystem.model.MedicalServiceEntity;
import com.example.medicalsystem.service.ServiceEntityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/services")
@Tag(name = "Услуги", description = "Медицинские услуги поликлиники")
public class ServiceController {

    private final ServiceEntityService serviceService;

    public ServiceController(ServiceEntityService serviceService) {
        this.serviceService = serviceService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DOCTOR', 'PATIENT')")
    public String list(@RequestParam(defaultValue = "1") int page,
                       @RequestParam(required = false) String searchField,
                       @RequestParam(required = false) String searchValue,
                       Model model,
                       @AuthenticationPrincipal UserDetails principal) {
        Page<MedicalServiceEntity> services = serviceService.search(searchField, searchValue, page);

        model.addAttribute("services", services.getContent());
        addPagination(model, services);
        model.addAttribute("searchField", searchField);
        model.addAttribute("searchValue", searchValue);
        boolean patientOnly = principal.getAuthorities().stream().anyMatch(a -> "PATIENT".equals(a.getAuthority()))
                && principal.getAuthorities().stream().noneMatch(a -> "ADMIN".equals(a.getAuthority()) || "DOCTOR".equals(a.getAuthority()));
        model.addAttribute("patientOnly", patientOnly);

        return "services";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DOCTOR')")
    public String showCreateForm(Model model) {
        model.addAttribute("service", new MedicalServiceEntity());
        return "serviceForm";
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DOCTOR')")
    public String save(@Valid @ModelAttribute("service") MedicalServiceEntity service,
                       BindingResult result,
                       Model model) {
        if (result.hasErrors()) {
            model.addAttribute("service", service);
            return "serviceForm";
        }
        serviceService.save(service);
        return "redirect:/services";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DOCTOR')")
    public String showEditForm(@PathVariable Integer id, Model model) {
        model.addAttribute("service", serviceService.findById(id));
        return "serviceForm";
    }

    @PostMapping("/update")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DOCTOR')")
    public String update(@RequestParam Integer id,
                         @Valid @ModelAttribute("service") MedicalServiceEntity service,
                         BindingResult result,
                         Model model) {
        if (result.hasErrors()) {
            service.setIdService(id);
            model.addAttribute("service", service);
            return "serviceForm";
        }
        service.setIdService(id);
        serviceService.save(service);
        return "redirect:/services";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DOCTOR')")
    public String delete(@PathVariable Integer id) {
        serviceService.deleteById(id);
        return "redirect:/services";
    }

    private void addPagination(Model model, Page<?> page) {
        model.addAttribute("currentPage", page.getNumber() + 1);
        model.addAttribute("totalPages", Math.max(page.getTotalPages(), 1));
        model.addAttribute("hasNext", page.hasNext());
        model.addAttribute("hasPrevious", page.hasPrevious());
    }
}
