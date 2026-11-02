package com.example.medicalsystem.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/medical-records")
@Tag(name = "Медицинские записи")
public class MedicalRecordApiController {
    private final CatalogApiService api;
    public MedicalRecordApiController(CatalogApiService api) { this.api = api; }

    @GetMapping @Operation(summary = "Список медицинских записей")
    public java.util.List<CatalogApiService.MedicalRecordDto> list() { return api.medicalRecords(); }
    @GetMapping("/{id}") @Operation(summary = "Медицинская запись по ID")
    public CatalogApiService.MedicalRecordDto get(@PathVariable Integer id) { return api.medicalRecord(id); }
    @PostMapping @Operation(summary = "Создать медицинскую запись")
    public CatalogApiService.MedicalRecordDto create(@Valid @RequestBody CatalogApiService.MedicalRecordDto body) { return api.createMedicalRecord(body); }
    @PutMapping("/{id}") @Operation(summary = "Изменить медицинскую запись")
    public CatalogApiService.MedicalRecordDto update(@PathVariable Integer id, @Valid @RequestBody CatalogApiService.MedicalRecordDto body) { return api.updateMedicalRecord(id, body); }
    @DeleteMapping("/{id}") @Operation(summary = "Удалить медицинскую запись")
    public void delete(@PathVariable Integer id) { api.deleteMedicalRecord(id); }
}
