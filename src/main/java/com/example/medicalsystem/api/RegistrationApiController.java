package com.example.medicalsystem.api;

import com.example.medicalsystem.dto.RegistrationForm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Регистрация")
public class RegistrationApiController {
    private final SystemApiService api;
    public RegistrationApiController(SystemApiService api) { this.api = api; }

    @PostMapping("/register") @Operation(summary = "Зарегистрировать пациента")
    public SystemApiService.PublicUserDto register(@Valid @RequestBody RegistrationForm request) { return api.register(request); }
}
