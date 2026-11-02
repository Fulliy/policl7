package com.example.medicalsystem.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Администрирование")
public class AdminApiController {
    private final SystemApiService api;
    public AdminApiController(SystemApiService api) { this.api = api; }

    @GetMapping("/users") @Operation(summary = "Список пользователей для администратора")
    public java.util.List<SystemApiService.PublicUserDto> users() { return api.adminUsers(); }
    @GetMapping("/users/{id}") @Operation(summary = "Пользователь для администратора")
    public SystemApiService.PublicUserDto user(@PathVariable Integer id) { return api.adminUser(id); }
    @PutMapping("/users/{id}/roles") @Operation(summary = "Изменить роли пользователя")
    public SystemApiService.PublicUserDto roles(@PathVariable Integer id, @RequestBody SystemApiService.RoleUpdateRequest body) { return api.updateRoles(id, body); }
}
