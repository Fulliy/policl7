package com.example.medicalsystem.controller;

import com.example.medicalsystem.model.Prescription;
import com.example.medicalsystem.service.MedicalRecordService;
import com.example.medicalsystem.service.PrescriptionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.medicalsystem.service.ServiceEntityService;
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
@RequestMapping("/prescriptions")
@PreAuthorize("hasAnyAuthority('ADMIN', 'DOCTOR')")
@Tag(name = "Назначения", description = "Назначения процедур и лекарств")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;
    private final MedicalRecordService recordService;
    private final ServiceEntityService serviceService;

    public PrescriptionController(PrescriptionService prescriptionService,
                                  MedicalRecordService recordService,
                                  ServiceEntityService serviceService) {
        this.prescriptionService = prescriptionService;
        this.recordService = recordService;
        this.serviceService = serviceService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page,
                       @RequestParam(required = false) String searchField,
                       @RequestParam(required = false) String searchValue,
                       Model model) {
        Page<Prescription> prescriptions = prescriptionService.search(searchField, searchValue, page);

        model.addAttribute("prescriptions", prescriptions.getContent());
        addPagination(model, prescriptions);
        model.addAttribute("searchField", searchField);
        model.addAttribute("searchValue", searchValue);

        return "prescriptions";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("prescription", new Prescription());
        model.addAttribute("medicalRecords", recordService.findAll());
        model.addAttribute("services", serviceService.findAll());
        return "prescriptionForm";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("prescription") Prescription prescription,
                       BindingResult result,
                       Model model) {
        if (result.hasErrors()) {
            model.addAttribute("prescription", prescription);
            model.addAttribute("medicalRecords", recordService.findAll());
            model.addAttribute("services", serviceService.findAll());
            return "prescriptionForm";
        }
        prescriptionService.save(prescription);
        return "redirect:/prescriptions";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Integer id, Model model) {
        model.addAttribute("prescription", prescriptionService.findById(id));
        model.addAttribute("medicalRecords", recordService.findAll());
        model.addAttribute("services", serviceService.findAll());
        return "prescriptionForm";
    }

    @PostMapping("/update")
    public String update(@RequestParam Integer id,
                         @Valid @ModelAttribute("prescription") Prescription prescription,
                         BindingResult result,
                         Model model) {
        if (result.hasErrors()) {
            prescription.setIdPrescription(id);
            model.addAttribute("prescription", prescription);
            model.addAttribute("medicalRecords", recordService.findAll());
            model.addAttribute("services", serviceService.findAll());
            return "prescriptionForm";
        }
        prescription.setIdPrescription(id);
        prescriptionService.save(prescription);
        return "redirect:/prescriptions";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Integer id) {
        prescriptionService.deleteById(id);
        return "redirect:/prescriptions";
    }

    private void addPagination(Model model, Page<?> page) {
        model.addAttribute("currentPage", page.getNumber() + 1);
        model.addAttribute("totalPages", Math.max(page.getTotalPages(), 1));
        model.addAttribute("hasNext", page.hasNext());
        model.addAttribute("hasPrevious", page.hasPrevious());
    }
}
