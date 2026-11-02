package com.example.medicalsystem.records.api;

import com.example.medicalsystem.records.model.Prescription;
import com.example.medicalsystem.records.service.MedicalRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/prescriptions")
@Tag(name = "Назначения")
public class PrescriptionApiController {
    private final MedicalRecordService service;
    public PrescriptionApiController(MedicalRecordService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Назначения, доступные текущему пользователю")
    public List<PrescriptionDto> list(Authentication auth) {
        return service.prescriptionsVisibleTo(auth).stream().map(PrescriptionDto::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить назначение с проверкой прав на медицинскую запись")
    public PrescriptionDto get(@PathVariable Integer id, Authentication auth) {
        return PrescriptionDto.from(service.getPrescription(id, auth));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','DOCTOR')")
    @Operation(summary = "Создать назначение")
    public PrescriptionDto create(@Valid @RequestBody PrescriptionRequest body, Authentication auth) {
        return PrescriptionDto.from(service.createPrescription(body.toRequest(), auth));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','DOCTOR')")
    @Operation(summary = "Изменить назначение")
    public PrescriptionDto update(@PathVariable Integer id, @Valid @RequestBody PrescriptionRequest body, Authentication auth) {
        return PrescriptionDto.from(service.updatePrescription(id, body.toRequest(), auth));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','DOCTOR')")
    @Operation(summary = "Удалить назначение")
    public void delete(@PathVariable Integer id, Authentication auth) { service.deletePrescription(id, auth); }

    public record PrescriptionRequest(String medicationName, String dosage, String frequency, Integer durationDays,
                                      @NotNull Integer medicalRecordId, @NotNull Integer serviceId) {
        MedicalRecordService.PrescriptionRequest toRequest() {
            return new MedicalRecordService.PrescriptionRequest(medicationName, dosage, frequency, durationDays,
                    medicalRecordId, serviceId);
        }
    }
    public record PrescriptionDto(Integer id, String medicationName, String dosage, String frequency,
                                  Integer durationDays, Integer medicalRecordId, Integer serviceId) {
        static PrescriptionDto from(Prescription value) {
            return new PrescriptionDto(value.getIdPrescription(), value.getMedicationName(), value.getDosage(),
                    value.getFrequency(), value.getDurationDays(), value.getMedicalRecordId(), value.getServiceId());
        }
    }
}
