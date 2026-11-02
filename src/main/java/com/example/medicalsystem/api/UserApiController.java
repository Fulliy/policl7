package com.example.medicalsystem.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Пользователи")
public class UserApiController {
    private final CatalogApiService api;
    public UserApiController(CatalogApiService api) { this.api = api; }

    @GetMapping @Operation(summary = "Список пользователей")
    public java.util.List<CatalogApiService.UserDto> list() { return api.users(); }
    @GetMapping("/{id}") @Operation(summary = "Пользователь по ID")
    public CatalogApiService.UserDto get(@PathVariable Integer id) { return api.user(id); }
    @PostMapping @Operation(summary = "Создать пользователя")
    public CatalogApiService.UserDto create(@Valid @RequestBody CatalogApiService.UserCreateRequest body) { return api.createUser(body); }
    @PutMapping("/{id}") @Operation(summary = "Изменить пользователя")
    public CatalogApiService.UserDto update(@PathVariable Integer id, @Valid @RequestBody CatalogApiService.UserCreateRequest body) { return api.updateUser(id, body); }
    @DeleteMapping("/{id}") @Operation(summary = "Удалить пользователя")
    public void delete(@PathVariable Integer id) { api.deleteUser(id); }
}
