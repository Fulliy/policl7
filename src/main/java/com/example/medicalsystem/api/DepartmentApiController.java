package com.example.medicalsystem.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/departments")
@Tag(name = "Отделения")
public class DepartmentApiController {
    private final CatalogApiService api;
    public DepartmentApiController(CatalogApiService api) { this.api = api; }

    @GetMapping @Operation(summary = "Список отделений")
    public java.util.List<CatalogApiService.DepartmentDto> list() { return api.departments(); }
    @GetMapping("/{id}") @Operation(summary = "Отделение по ID")
    public CatalogApiService.DepartmentDto get(@PathVariable Integer id) { return api.department(id); }
    @PostMapping @Operation(summary = "Создать отделение")
    public CatalogApiService.DepartmentDto create(@Valid @RequestBody CatalogApiService.DepartmentDto body) { return api.createDepartment(body); }
    @PutMapping("/{id}") @Operation(summary = "Изменить отделение")
    public CatalogApiService.DepartmentDto update(@PathVariable Integer id, @Valid @RequestBody CatalogApiService.DepartmentDto body) { return api.updateDepartment(id, body); }
    @DeleteMapping("/{id}") @Operation(summary = "Удалить отделение")
    public void delete(@PathVariable Integer id) { api.deleteDepartment(id); }
}
