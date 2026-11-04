package com.example.medicalsystem.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/prescriptions")
@Tag(name = "Назначения")
public class PrescriptionApiController {
    private final CatalogApiService api;
    public PrescriptionApiController(CatalogApiService api) { this.api = api; }

    @GetMapping @Operation(summary = "Список назначений")
    public java.util.List<CatalogApiService.PrescriptionDto> list(Authentication auth) { return api.prescriptions(auth); }
    @GetMapping("/{id}") @Operation(summary = "Назначение по ID")
    public CatalogApiService.PrescriptionDto get(@PathVariable Integer id, Authentication auth) { return api.prescription(id, auth); }
    @PostMapping @Operation(summary = "Создать назначение")
    public CatalogApiService.PrescriptionDto create(@Valid @RequestBody CatalogApiService.PrescriptionDto body, Authentication auth) { return api.createPrescription(body, auth); }
    @PutMapping("/{id}") @Operation(summary = "Изменить назначение")
    public CatalogApiService.PrescriptionDto update(@PathVariable Integer id, @Valid @RequestBody CatalogApiService.PrescriptionDto body, Authentication auth) { return api.updatePrescription(id, body, auth); }
    @DeleteMapping("/{id}") @Operation(summary = "Удалить назначение")
    public void delete(@PathVariable Integer id, Authentication auth) { api.deletePrescription(id, auth); }
}
