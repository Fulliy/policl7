package com.example.medicalsystem.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/roles")
@Tag(name = "Роли")
public class RoleApiController {
    private final CatalogApiService api;
    public RoleApiController(CatalogApiService api) { this.api = api; }

    @GetMapping @Operation(summary = "Список ролей")
    public java.util.List<CatalogApiService.RoleDto> list() { return api.roles(); }
    @GetMapping("/{id}") @Operation(summary = "Роль по ID")
    public CatalogApiService.RoleDto get(@PathVariable Integer id) { return api.role(id); }
    @PostMapping @Operation(summary = "Создать роль")
    public CatalogApiService.RoleDto create(@Valid @RequestBody CatalogApiService.RoleDto body) { return api.createRole(body); }
    @PutMapping("/{id}") @Operation(summary = "Изменить роль")
    public CatalogApiService.RoleDto update(@PathVariable Integer id, @Valid @RequestBody CatalogApiService.RoleDto body) { return api.updateRole(id, body); }
    @DeleteMapping("/{id}") @Operation(summary = "Удалить роль")
    public void delete(@PathVariable Integer id) { api.deleteRole(id); }
}
