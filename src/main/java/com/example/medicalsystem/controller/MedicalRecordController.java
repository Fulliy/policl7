package com.example.medicalsystem.controller;

import com.example.medicalsystem.model.MedicalRecord;
import com.example.medicalsystem.service.MedicalRecordService;
import com.example.medicalsystem.service.AppointmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/medicalRecords")
@PreAuthorize("hasAnyAuthority('ADMIN', 'DOCTOR')")
@Tag(name = "Медицинские записи", description = "История болезни пациентов")
public class MedicalRecordController {

    private final MedicalRecordService recordService;
    private final AppointmentService appointmentService;

    public MedicalRecordController(MedicalRecordService recordService,
                                   AppointmentService appointmentService) {
        this.recordService = recordService;
        this.appointmentService = appointmentService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page,
                       @RequestParam(required = false) String searchField,
                       @RequestParam(required = false) String searchValue,
                       Model model) {
        Page<MedicalRecord> records = recordService.search(searchField, searchValue, page);

        model.addAttribute("medicalRecords", records.getContent());
        addPagination(model, records);
        model.addAttribute("searchField", searchField);
        model.addAttribute("searchValue", searchValue);

        return "medicalRecords";
    }

    @GetMapping("/new")
    public String showCreateForm(@RequestParam(required = false) Integer appointmentId, Model model) {
        MedicalRecord record = new MedicalRecord();
        if (appointmentId != null) {
            record.setAppointment(appointmentService.findById(appointmentId));
        }
        model.addAttribute("medicalRecord", record);
        model.addAttribute("appointments", appointmentService.findAll());
        return "medicalRecordForm";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("medicalRecord") MedicalRecord record,
                       BindingResult result,
                       Model model) {
        if (result.hasErrors()) {
            model.addAttribute("medicalRecord", record);
            model.addAttribute("appointments", appointmentService.findAll());
            return "medicalRecordForm";
        }
        recordService.save(record);
        return "redirect:/medicalRecords";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Integer id, Model model) {
        model.addAttribute("medicalRecord", recordService.findById(id));
        model.addAttribute("appointments", appointmentService.findAll());
        return "medicalRecordForm";
    }

    @PostMapping("/update")
    public String update(@RequestParam Integer id,
                         @Valid @ModelAttribute("medicalRecord") MedicalRecord record,
                         BindingResult result,
                         Model model) {
        if (result.hasErrors()) {
            record.setIdRecord(id);
            model.addAttribute("medicalRecord", record);
            model.addAttribute("appointments", appointmentService.findAll());
            return "medicalRecordForm";
        }
        record.setIdRecord(id);
        recordService.save(record);
        return "redirect:/medicalRecords";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Integer id) {
        recordService.deleteById(id);
        return "redirect:/medicalRecords";
    }

    private void addPagination(Model model, Page<?> page) {
        model.addAttribute("currentPage", page.getNumber() + 1);
        model.addAttribute("totalPages", Math.max(page.getTotalPages(), 1));
        model.addAttribute("hasNext", page.hasNext());
        model.addAttribute("hasPrevious", page.hasPrevious());
    }
}
