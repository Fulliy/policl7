package com.example.medicalsystem.records.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "Medical Record Service API", version = "1.0",
        description = "Медицинские записи и назначения с проверкой прав доступа"))
public class OpenApiConfiguration { }
