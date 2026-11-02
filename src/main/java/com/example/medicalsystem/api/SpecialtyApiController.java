package com.example.medicalsystem.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/specialties")
@Tag(name = "Специальности")
public class SpecialtyApiController {
    private final CatalogApiService api;
    public SpecialtyApiController(CatalogApiService api) { this.api = api; }

    @GetMapping @Operation(summary = "Список специальностей")
    public java.util.List<CatalogApiService.SpecialtyDto> list() { return api.specialties(); }
    @GetMapping("/{id}") @Operation(summary = "Специальность по ID")
    public CatalogApiService.SpecialtyDto get(@PathVariable Integer id) { return api.specialty(id); }
    @PostMapping @Operation(summary = "Создать специальность")
    public CatalogApiService.SpecialtyDto create(@Valid @RequestBody CatalogApiService.SpecialtyDto body) { return api.createSpecialty(body); }
    @PutMapping("/{id}") @Operation(summary = "Изменить специальность")
    public CatalogApiService.SpecialtyDto update(@PathVariable Integer id, @Valid @RequestBody CatalogApiService.SpecialtyDto body) { return api.updateSpecialty(id, body); }
    @DeleteMapping("/{id}") @Operation(summary = "Удалить специальность")
    public void delete(@PathVariable Integer id) { api.deleteSpecialty(id); }
}
