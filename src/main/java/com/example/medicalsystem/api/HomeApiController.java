package com.example.medicalsystem.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Главная")
public class HomeApiController {
    private final SystemApiService api;
    public HomeApiController(SystemApiService api) { this.api = api; }

    @GetMapping("/home") @Operation(summary = "Статус API")
    public Map<String, String> status() { return api.home(); }
}
