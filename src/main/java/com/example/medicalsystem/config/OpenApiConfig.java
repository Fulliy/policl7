package com.example.medicalsystem.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Medical System API",
                version = "1.0",
                description = "API медицинской информационной системы поликлиники"
        )
)
public class OpenApiConfig {
}
