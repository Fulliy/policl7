package com.example.medicalsystem.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/medical-records")
@Tag(name = "Медицинские записи")
public class MedicalRecordApiController {
    private final CatalogApiService api;
    public MedicalRecordApiController(CatalogApiService api) { this.api = api; }

    @GetMapping @Operation(summary = "Список медицинских записей")
    public java.util.List<CatalogApiService.MedicalRecordDto> list(Authentication auth) { return api.medicalRecords(auth); }
    @GetMapping("/{id}") @Operation(summary = "Медицинская запись по ID")
    public CatalogApiService.MedicalRecordDto get(@PathVariable Integer id, Authentication auth) { return api.medicalRecord(id, auth); }
    @PostMapping @Operation(summary = "Создать медицинскую запись")
    public CatalogApiService.MedicalRecordDto create(@Valid @RequestBody CatalogApiService.MedicalRecordDto body, Authentication auth) { return api.createMedicalRecord(body, auth); }
    @PutMapping("/{id}") @Operation(summary = "Изменить медицинскую запись")
    public CatalogApiService.MedicalRecordDto update(@PathVariable Integer id, @Valid @RequestBody CatalogApiService.MedicalRecordDto body, Authentication auth) { return api.updateMedicalRecord(id, body, auth); }
    @DeleteMapping("/{id}") @Operation(summary = "Удалить медицинскую запись")
    public void delete(@PathVariable Integer id, Authentication auth) { api.deleteMedicalRecord(id, auth); }
}
