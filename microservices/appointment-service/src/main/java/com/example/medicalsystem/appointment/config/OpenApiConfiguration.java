package com.example.medicalsystem.appointment.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "Appointment Service API", version = "1.0",
        description = "Записи на приём и медицинские услуги"))
public class OpenApiConfiguration { }
