package com.example.medicalsystem.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/prescriptions")
@Tag(name = "Назначения")
public class PrescriptionApiController {
    private final CatalogApiService api;
    public PrescriptionApiController(CatalogApiService api) { this.api = api; }

    @GetMapping @Operation(summary = "Список назначений")
    public java.util.List<CatalogApiService.PrescriptionDto> list() { return api.prescriptions(); }
    @GetMapping("/{id}") @Operation(summary = "Назначение по ID")
    public CatalogApiService.PrescriptionDto get(@PathVariable Integer id) { return api.prescription(id); }
    @PostMapping @Operation(summary = "Создать назначение")
    public CatalogApiService.PrescriptionDto create(@Valid @RequestBody CatalogApiService.PrescriptionDto body) { return api.createPrescription(body); }
    @PutMapping("/{id}") @Operation(summary = "Изменить назначение")
    public CatalogApiService.PrescriptionDto update(@PathVariable Integer id, @Valid @RequestBody CatalogApiService.PrescriptionDto body) { return api.updatePrescription(id, body); }
    @DeleteMapping("/{id}") @Operation(summary = "Удалить назначение")
    public void delete(@PathVariable Integer id) { api.deletePrescription(id); }
}
