package com.example.medicalsystem.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/services")
@Tag(name = "Медицинские услуги")
public class ServiceApiController {
    private final CatalogApiService api;
    public ServiceApiController(CatalogApiService api) { this.api = api; }

    @GetMapping @Operation(summary = "Список медицинских услуг")
    public java.util.List<CatalogApiService.ServiceDto> list() { return api.services(); }
    @GetMapping("/{id}") @Operation(summary = "Медицинская услуга по ID")
    public CatalogApiService.ServiceDto get(@PathVariable Integer id) { return api.service(id); }
    @PostMapping @Operation(summary = "Создать медицинскую услугу")
    public CatalogApiService.ServiceDto create(@Valid @RequestBody CatalogApiService.ServiceDto body) { return api.createService(body); }
    @PutMapping("/{id}") @Operation(summary = "Изменить медицинскую услугу")
    public CatalogApiService.ServiceDto update(@PathVariable Integer id, @Valid @RequestBody CatalogApiService.ServiceDto body) { return api.updateService(id, body); }
    @DeleteMapping("/{id}") @Operation(summary = "Удалить медицинскую услугу")
    public void delete(@PathVariable Integer id) { api.deleteService(id); }
}
